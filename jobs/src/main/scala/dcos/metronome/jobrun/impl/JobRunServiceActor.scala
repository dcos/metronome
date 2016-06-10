package dcos.metronome.jobrun.impl

import akka.actor.{ Actor, ActorRef, Props, Stash }
import dcos.metronome.JobRunDoesNotExist
import dcos.metronome.behavior.{ ActorMetrics, Behavior }
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model._
import dcos.metronome.repository.{ LoadContentOnStartup, Repository }
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.task.bus.TaskChangeObservables.TaskChanged
import mesosphere.marathon.state.PathId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise

/**
  * Knows and manages all active JobRunExecutors.
  */
class JobRunServiceActor(
    clock:           Clock,
    executorFactory: (JobRun, Promise[JobResult]) => Props,
    val repo:        Repository[JobRunId, JobRun], //TODO: remove the repo
    val behavior:    Behavior
) extends Actor with LoadContentOnStartup[JobRunId, JobRun] with Stash with ActorMetrics {

  import JobRunExecutorActor._
  import JobRunServiceActor._

  private[impl] val allJobRuns = TrieMap.empty[JobRunId, StartedJobRun]
  private[impl] val allRunExecutors = TrieMap.empty[JobRunId, ActorRef]

  override def receive: Receive = around {
    // api messages
    case ListRuns(promise)                    => promise.success(allJobRuns.values)
    case GetJobRun(id, promise)               => promise.success(allJobRuns.get(id))
    case GetActiveJobRuns(specId, promise)    => promise.success(runsForSpec(specId))
    case KillJobRun(id, promise)              => killJobRun(id, promise)

    // trigger messages
    case TriggerJobRun(spec, promise)         => triggerJobRun(spec, promise)

    // executor messages
    case JobRunUpdate(started)                => updateJobRun(started)
    case JobRunFinished(result)               => jobRunFinished(result)
    case JobRunAborted(result)                => jobRunAborted(result)

    // Core messages
    case NotifyOfUpdate(taskChanged, promise) => forwardUpdate(taskChanged, promise)
  }

  def runsForSpec(specId: PathId): Iterable[StartedJobRun] = allJobRuns.values.filter(_.jobRun.jobSpec.id == specId)

  def triggerJobRun(spec: JobSpec, promise: Promise[StartedJobRun]): Unit = {
    log.info(s"Trigger new JobRun for JobSpec: $spec")
    val jobRun = new JobRun(JobRunId(spec), spec, JobRunStatus.Starting, clock.now(), None, Seq.empty)
    val startedJobRun = startJobRun(jobRun)
    promise.success(startedJobRun)
  }

  def startJobRun(jobRun: JobRun): StartedJobRun = {
    log.info(s"Start new JobRun: ${jobRun.id}")
    val resultPromise = Promise[JobResult]()

    // create new executor and store reference
    val executor = context.actorOf(executorFactory(jobRun, resultPromise), s"executor:${jobRun.id}")
    context.watch(executor)
    allRunExecutors += jobRun.id -> executor

    // create new started job run and store reference
    val startedJobRun = StartedJobRun(jobRun, resultPromise.future)
    allJobRuns += jobRun.id -> startedJobRun
    context.system.eventStream.publish(Event.JobRunStarted(jobRun))
    startedJobRun
  }

  def updateJobRun(started: StartedJobRun): Unit = {
    context.system.eventStream.publish(Event.JobRunUpdate(started.jobRun))
    allJobRuns += started.jobRun.id -> started
  }

  def killJobRun(id: JobRunId, promise: Promise[StartedJobRun]): Unit = {
    log.info(s"Request kill of job run $id")
    withJobExecutor(id, promise) { (executor, run) =>
      executor ! KillCurrentJobRun
      promise.success(run)
    }
  }

  def jobRunFinished(result: JobResult): Unit = {
    context.system.eventStream.publish(Event.JobRunFinished(result.jobRun))
    wipeJobRun(result.jobRun.id)
  }

  def jobRunAborted(result: JobResult): Unit = {
    context.system.eventStream.publish(Event.JobRunFailed(result.jobRun))
    wipeJobRun(result.jobRun.id)
  }

  def wipeJobRun(id: JobRunId): Unit = {
    allJobRuns -= id
    allRunExecutors.get(id).foreach { executor =>
      context.unwatch(executor)
      context.stop(executor)
      allRunExecutors -= id
    }
  }

  def forwardUpdate(taskChanged: TaskChanged, promise: Promise[Unit]): Unit = {
    // FIXME (glue): provide conversion from taskId to JobRunId (let taskId be JobRunId#attempt)
    val id = taskChanged.taskId.idString.replaceFirst("^.*[.]", "")
    val jobRunId = JobRunId(taskChanged.runSpecId, id)

    allRunExecutors.get(jobRunId) match {
      case Some(actorRef) =>
        actorRef ! TaskChangedUpdate(taskChanged, promise)
      case None =>
        promise.failure(JobRunDoesNotExist(jobRunId))
    }
  }

  def withJobExecutor[T](id: JobRunId, promise: Promise[StartedJobRun])(fn: (ActorRef, StartedJobRun) => T): Option[T] = {
    val result = for {
      executor <- allRunExecutors.get(id)
      startedRun <- allJobRuns.get(id)
    } yield fn(executor, startedRun)
    if (result.isEmpty) promise.failure(JobRunDoesNotExist(id))
    result
  }

  override def initialize(runs: List[JobRun]): Unit = {
    runs.foreach(startJobRun)
  }
}

object JobRunServiceActor {

  case class ListRuns(promise: Promise[Iterable[StartedJobRun]])
  case class GetJobRun(id: JobRunId, promise: Promise[Option[StartedJobRun]])
  case class GetActiveJobRuns(jobId: PathId, promise: Promise[Iterable[StartedJobRun]])
  case class KillJobRun(id: JobRunId, promise: Promise[StartedJobRun])
  case class TriggerJobRun(spec: JobSpec, promise: Promise[StartedJobRun])
  case class NotifyOfUpdate(taskChanged: TaskChanged, promise: Promise[Unit])

  def props(
    clock:           Clock,
    executorFactory: (JobRun, Promise[JobResult]) => Props,
    repo:            Repository[JobRunId, JobRun],
    behavior:        Behavior
  ): Props = Props(new JobRunServiceActor(clock, executorFactory, repo, behavior))

}

package dcos.metronome
package jobrun.impl

import akka.actor._
import dcos.metronome.JobRunDoesNotExist
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model._
import dcos.metronome.repository.{ LoadContentOnStartup, Repository }
import dcos.metronome.utils.time.Clock

import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise
import scala.concurrent.duration.Duration

/**
  * Knows and manages all active JobRunExecutors.
  */
class JobRunServiceActor(
  clock:           Clock,
  executorFactory: (JobRun, Promise[JobResult]) => Props,
  val repo:        Repository[JobRunId, JobRun], //TODO: remove the repo
  val behavior:    Behavior) extends Actor with LoadContentOnStartup[JobRunId, JobRun] with Stash with ActorBehavior {

  import JobRunExecutorActor._
  import JobRunServiceActor._

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[TaskStateChangedEvent])
  }

  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self)
  }

  private[impl] val allJobRuns = TrieMap.empty[JobRunId, StartedJobRun]
  private[impl] val allRunExecutors = TrieMap.empty[JobRunId, ActorRef]
  private[impl] val actorsWaitingForKill = TrieMap.empty[JobRunId, Set[ActorRef]].withDefaultValue(Set.empty)

  override def receive: Receive = around {
    // api messages
    case ListRuns(filter)                      => sender() ! allJobRuns.values.filter(startedJobRun => filter(startedJobRun.jobRun))
    case GetJobRun(id)                         => sender() ! allJobRuns.get(id)
    case GetActiveJobRuns(specId)              => sender() ! runsForJob(specId)
    case KillJobRun(id)                        => killJobRun(id)

    // trigger messages
    case TriggerJobRun(spec, startingDeadline) => triggerJobRun(spec, startingDeadline)

    // executor messages
    case JobRunUpdate(started)                 => updateJobRun(started)
    case Finished(result)                      => jobRunFinished(result)
    case Aborted(result)                       => jobRunFailed(result)
    case Failed(result)                        => jobRunFailed(result)

    //event stream events
    case update: TaskStateChangedEvent         => forwardStatusUpdate(update)
  }

  def runsForJob(jobId: JobId): Iterable[StartedJobRun] = allJobRuns.values.filter(_.jobRun.id.jobId == jobId)

  def triggerJobRun(spec: JobSpec, startingDeadline: Option[Duration]): Unit = {
    log.info(s"Trigger new JobRun for JobSpec: $spec")
    val jobRun = JobRun(JobRunId(spec), spec, JobRunStatus.Initial, clock.now(), None, startingDeadline, Map.empty)
    val startedJobRun = startJobRun(jobRun)
    sender() ! startedJobRun
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

  def killJobRun(id: JobRunId): Unit = {
    log.info(s"Request kill of job run $id")
    withJobExecutor(id) { (executor, run) =>
      executor ! KillCurrentJobRun
      actorsWaitingForKill += id -> (actorsWaitingForKill(id) + sender())
    }
  }

  def jobRunFinished(result: JobResult): Unit = {
    log.info("JobRun finished")
    context.system.eventStream.publish(Event.JobRunFinished(result.jobRun))
    wipeJobRun(result.jobRun.id)
  }

  def jobRunFailed(result: JobResult): Unit = {
    log.info("JobRun failed or aborted")
    context.system.eventStream.publish(Event.JobRunFailed(result.jobRun))
    wipeJobRun(result.jobRun.id)
  }

  def wipeJobRun(id: JobRunId): Unit = {
    val jobRun = allJobRuns(id)
    allJobRuns -= id
    allRunExecutors.get(id).foreach { executor =>
      context.unwatch(executor)
      context.stop(executor)
      allRunExecutors -= id
      log.debug("{} now shutdown and removed from registry.", executor)
    }
    //send all actors waiting for kill the jobRun
    actorsWaitingForKill.remove(id).foreach(_.foreach(_ ! jobRun))
  }

  def forwardStatusUpdate(update: TaskStateChangedEvent): Unit = {
    val jobRunId = JobRunId(update.taskId.runSpecId)

    allRunExecutors.get(jobRunId) match {
      case Some(actorRef) =>
        log.debug("Forwarding status update to {}", actorRef.path)
        actorRef ! ForwardStatusUpdate(update)
      case None =>
        log.debug("Ignoring TaskStateChangedEvent for {}. No one interested.", jobRunId)
    }
  }

  def withJobExecutor[T](id: JobRunId)(fn: (ActorRef, StartedJobRun) => T): Option[T] = {
    val result = for {
      executor <- allRunExecutors.get(id)
      startedRun <- allJobRuns.get(id)
    } yield fn(executor, startedRun)
    if (result.isEmpty) sender() ! Status.Failure(JobRunDoesNotExist(id))
    result
  }

  override def initialize(runs: List[JobRun]): Unit = {
    // FIXME: we should to wait until the Reconciliation has finished!
    runs.foreach(r => startJobRun(r))
  }
}

object JobRunServiceActor {

  case class ListRuns(filter: JobRun => Boolean)
  case class GetJobRun(id: JobRunId)
  case class GetActiveJobRuns(jobId: JobId)
  case class KillJobRun(id: JobRunId)
  case class TriggerJobRun(jobSpec: JobSpec, startingDeadline: Option[Duration])

  def props(
    clock:           Clock,
    executorFactory: (JobRun, Promise[JobResult]) => Props,
    repo:            Repository[JobRunId, JobRun],
    behavior:        Behavior): Props = Props(new JobRunServiceActor(clock, executorFactory, repo, behavior))

}

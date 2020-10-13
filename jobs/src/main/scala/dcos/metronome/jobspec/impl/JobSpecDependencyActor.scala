package dcos.metronome
package jobspec.impl

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props, Stash, Status}
import akka.pattern.pipe
import dcos.metronome.jobrun.{JobRunService, StartedJobRun}
import dcos.metronome.jobspec.impl.JobSpecDependencyActor.UpdateJobSpec
import dcos.metronome.model.{Event, JobId, JobRun, JobRunStatus, JobSpec}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

/**
  * Manages one JobSpec.
  *
  * If the JobSpec has dependencies, it subscribes to the events of its dependencies, ie parents.
  *
  * This actor is analog to [[JobSpecSchedulerActor]].
  *
  * TODO: find a better name
  */
class JobSpecDependencyActor(initSpec: JobSpec, runService: JobRunService) extends Actor with Stash with ActorLogging {

  private[impl] var spec = initSpec

  // Time when this job spec finished successfully the last time.
  private[impl] var lastSuccessfulRun: Instant = Instant.MIN

  // Time when this job spec was triggered the last time.
  private[impl] var lastStartedAt: Option[Instant] = None

  // An index of all parents
  private[impl] var dependencyIndex: Set[JobId] = spec.dependencies.toSet

  private[impl] var currentSpec: JobSpec = initSpec

  // State of latest parents' runs.
  val lastParentRuns: mutable.Map[JobId, JobRun] = mutable.Map.empty

  implicit val ec: ExecutionContext = context.dispatcher

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[Event.JobRunEvent])
    context.system.eventStream.subscribe(self, classOf[Event.JobSpecUpdated])

    // Get active run for this job.
    // TODO: load history
    runService.listRuns(jobRun => jobRun.id.jobId == spec.id).pipeTo(self)
    log.info(s"Started for job ${spec.id}: id=${spec.id} dependencies=[${dependencyIndex.mkString(", ")}]")
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = initializing

  def initializing: Receive = {
    case activeRuns: Iterable[StartedJobRun] =>
      lastStartedAt = activeRuns.map(_.jobRun.createdAt).toVector.sorted.lastOption
      context.become(initialized)
      unstashAll()

    case Status.Failure(cause) =>
      // escalate this failure
      throw new IllegalStateException(s"while starting job ${spec.id}", cause)

    case other =>
      log.debug(s"Stashing $other")
      stash()
  }

  def initialized: Receive = {
    case ev: Event.JobRunEvent =>
      update(ev.jobRun)
      if (shouldTriggerJob()) {
        log.info(
          s"Start next run of job ${spec.id}, all parents finished successfully lastParentRuns=${lastParentRuns.values}"
        )
        runService.startJobRun(currentSpec).pipeTo(self)
        context.become(starting)
      }

    case ev: Event.JobSpecUpdated =>
      currentSpec = ev.job

    case UpdateJobSpec(newJobSpec) =>
      log.debug(s"Update job spec. id=${newJobSpec.id}")
      require(newJobSpec.id == spec.id)
      updateJobSpec(newJobSpec)
  }

  /**
    * State when the job was triggered. All messages are stashed until the job either finishes or fails.
    * This means we cannot have concurrent job runs of this job.
    */
  def starting: Receive = {
    case StartedJobRun(run, _) =>
      require(run.id.jobId == spec.id)
      lastStartedAt = Some(run.createdAt)
      context.become(initialized)
      unstashAll()

    case Status.Failure(cause) =>
      // escalate this failure
      throw new IllegalStateException(s"while starting job ${spec.id}", cause)

    case other =>
      log.debug(s"Stashing $other")
      stash()
  }

  def update(jobRun: JobRun): Unit = {
    log.debug(s"Updating state: jobRun=$jobRun")
    if (dependencyIndex.contains(jobRun.id.jobId)) {
      lastParentRuns.update(jobRun.id.jobId, jobRun)
    } else if (jobRun.id.jobId == spec.id) {
      if (jobRun.status == JobRunStatus.Success) {
        lastSuccessfulRun = jobRun.completedAt.get
      }
      // TODO: handle error case
    }
  }

  /**
    * @return true if the last successful run of the child is older than all parent runs.
    */
  def shouldTriggerJob(): Boolean = {
    log.debug(
      s"Should trigger job: id${spec.id} lastRun=$lastSuccessfulRun index=$dependencyIndex lastDependencyRuns=$lastParentRuns"
    )
    val allParentsSuccessful = dependencyIndex.forall { p =>
      lastParentRuns.get(p).exists(_.status == JobRunStatus.Success)
    }
    val lastSuccessfulRunOlderThanParents = lastParentRuns.valuesIterator.forall { lastRun =>
      lastRun.completedAt.exists(_.isAfter(lastSuccessfulRun))
    }
    val didNotTriggerJobYet = !lastStartedAt.exists(_.isAfter(lastSuccessfulRun))

    allParentsSuccessful && lastSuccessfulRunOlderThanParents && didNotTriggerJobYet
  }

  def updateJobSpec(newJobSpec: JobSpec): Unit = {
    spec = newJobSpec
    dependencyIndex = newJobSpec.dependencies.toSet

    // Remove dependencies from last successful runs
    lastParentRuns.keySet.diff(dependencyIndex).foreach(lastParentRuns.remove)
  }
}

object JobSpecDependencyActor {

  case class UpdateJobSpec(newSpec: JobSpec)

  def props(spec: JobSpec, runService: JobRunService): Props = {
    Props(new JobSpecDependencyActor(spec, runService))
  }
}

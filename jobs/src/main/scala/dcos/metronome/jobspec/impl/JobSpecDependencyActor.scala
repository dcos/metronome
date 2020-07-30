package dcos.metronome
package jobspec.impl

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props, Stash}
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.impl.JobSpecDependencyActor.{DependenciesState, UpdateJobSpec}
import dcos.metronome.model.{Event, JobId, JobRun, JobRunStatus, JobSpec}

import scala.collection.mutable

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
  private[impl] var lastSuccessfulRun: Instant = Instant.MIN
  val dependenciesState = DependenciesState(initSpec.dependencies.toSet)

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[Event.JobRunEvent])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case Event.JobRunFinished(jobRun, _, _) if jobRun.id.jobId == initSpec.id =>
      lastSuccessfulRun = jobRun.completedAt.getOrElse(Instant.MIN)

    case Event.JobRunFailed(jobRun, _, _) if jobRun.id.jobId == initSpec.id =>
      lastSuccessfulRun = Instant.MIN

    case ev: Event.JobRunEvent =>
      dependenciesState.update(ev.jobRun)
      if (dependenciesState.shouldTriggerJob(lastSuccessfulRun)) {
        runService.startJobRun(initSpec)
      }

    case UpdateJobSpec(newJobSpec) =>
      log.debug(s"Update job spec. id=${newJobSpec.id}")
      require(newJobSpec.id == spec.id)
      spec = newJobSpec
      dependenciesState.updateJobSpec(newJobSpec)

  }
}

object JobSpecDependencyActor {

  case class UpdateJobSpec(newSpec: JobSpec)

  case class DependenciesState(dependencies: Set[JobId]) extends StrictLogging {

    // An index of all parents
    private[impl] var dependencyIndex: Set[JobId] = dependencies

    // State of all successful parents runs.
    val lastSuccessfulRunDependencies: mutable.Map[JobId, Instant] = mutable.Map.empty

    def update(jobRun: JobRun): Unit = {
      if (dependencyIndex.contains(jobRun.id.jobId)) {
        if (jobRun.status == JobRunStatus.Success) {
          lastSuccessfulRunDependencies.update(jobRun.id.jobId, jobRun.completedAt.get)
        } else if (jobRun.status == JobRunStatus.Failed) {
          lastSuccessfulRunDependencies.remove(jobRun.jobSpec.id)
        }
      }
    }

    /**
      * @return true if the last successful run of the child is older than all parent runs.
      */
    def shouldTriggerJob(lastSuccessfulRun: Instant): Boolean = {
      logger.debug(
        s"Should trigger: lastRun=$lastSuccessfulRun index=$dependencyIndex lastDependencyRuns=$lastSuccessfulRunDependencies"
      )
      lastSuccessfulRunDependencies.keySet == dependencyIndex && lastSuccessfulRunDependencies.values.forall(
        _.isAfter(lastSuccessfulRun)
      )
    }

    def updateJobSpec(newJobSpec: JobSpec): Unit = {
      dependencyIndex = newJobSpec.dependencies.toSet

      // Remove dependencies from last successful runs
      lastSuccessfulRunDependencies.keySet.diff(dependencyIndex).foreach(lastSuccessfulRunDependencies.remove)
    }
  }

  def props(spec: JobSpec, runService: JobRunService): Props = {
    Props(new JobSpecDependencyActor(spec, runService))
  }
}

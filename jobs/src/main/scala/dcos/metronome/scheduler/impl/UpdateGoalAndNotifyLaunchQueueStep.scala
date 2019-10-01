package dcos.metronome.scheduler.impl

import akka.Done
import com.google.inject.Provider
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobSpec, RestartPolicy }
import mesosphere.marathon.core.condition.Condition
import mesosphere.marathon.core.instance.update.{ InstanceChange, InstanceChangeHandler }
import mesosphere.marathon.core.instance.{ Goal, GoalChangeReason }
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.tracker.InstanceTracker
import org.slf4j.LoggerFactory

import scala.async.Async.{ async, await }
import scala.concurrent.{ ExecutionContext, Future }

class UpdateGoalAndNotifyLaunchQueueStep(
  jobSpecServiceProvider:  Provider[JobSpecService],
  instanceTrackerProvider: Provider[InstanceTracker],
  launchQueueProvider:     Provider[LaunchQueue]) extends InstanceChangeHandler {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def name: String = "UpdateGoalOnFinishOperationStep"
  override def metricName: String = "UpdateGoalOnFinishOperationStep"

  override def process(instanceChange: InstanceChange): Future[Done] = async {
    log.info(s"Received update on instance ${instanceChange.instance} ")

    val jobSpecOpt: Option[JobSpec] = await(jobSpecServiceProvider.get().getJobSpec(JobId(instanceChange.runSpecId)))

    log.info(s"Loaded jobSpec for ${instanceChange.runSpecId} => ${jobSpecOpt}")

    instanceChange.condition match {
      case Condition.Finished if jobSpecOpt.isDefined =>
        log.info(" ==> Condition is finished, and goal is not yet stopped. Swallow the update and set Goal to Decommissioned")
        instanceTrackerProvider.get.setGoal(instanceChange.id, Goal.Decommissioned, GoalChangeReason.DeletingApp)
      case Condition.Failed if jobSpecOpt.exists(_.run.restart.policy == RestartPolicy.Never) =>
        log.info(" ==> Condition is failed and restart policy is Never, and goal is not yet stopped. Swallow the update and set Goal to Decommissioned")
        instanceTrackerProvider.get.setGoal(instanceChange.id, Goal.Decommissioned, GoalChangeReason.DeletingApp)
      case _ =>
        log.info(" ==> Different conditions, notify launchQueueProvider")
        launchQueueProvider.get().notifyOfInstanceUpdate(instanceChange)
    }

    // Send a new Future, otherwise we're deadlocking...
    Done
  }(ExecutionContext.global) // TODO AN: Which execution context to use?

}

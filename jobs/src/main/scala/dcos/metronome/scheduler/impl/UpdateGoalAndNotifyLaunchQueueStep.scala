package dcos.metronome.scheduler.impl

import akka.Done
import com.google.inject.Provider
import mesosphere.marathon.core.condition.Condition
import mesosphere.marathon.core.instance.update.{ InstanceChange, InstanceChangeHandler }
import mesosphere.marathon.core.instance.{ Goal, GoalChangeReason }
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.tracker.InstanceTracker
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class UpdateGoalAndNotifyLaunchQueueStep(instanceTrackerProvider: Provider[InstanceTracker], launchQueueProvider: Provider[LaunchQueue]) extends InstanceChangeHandler {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def name: String = "UpdateGoalOnFinishOperationStep"
  override def metricName: String = "UpdateGoalOnFinishOperationStep"

  override def process(instanceChange: InstanceChange): Future[Done] = {
    log.info(s"Received update on instance ${instanceChange.instance} ")

    if (instanceChange.condition == Condition.Finished && instanceChange.instance.state.goal != Goal.Stopped) {
      log.info(" ==> Condition is finished, and goal is not yet stopped. Swallow the update and set Goal to Stopped")
      instanceTrackerProvider.get.setGoal(instanceChange.id, Goal.Stopped, GoalChangeReason.DeletingApp)
    } else {
      log.info(" ==> Different conditions, notify launchQueueProvider")
      launchQueueProvider.get().notifyOfInstanceUpdate(instanceChange)
    }

    // Send a new Future, otherwise we're deadlocking...
    Future.successful(Done)
  }

}

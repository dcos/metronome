package dcos.metronome
package scheduler.impl

import akka.Done
import akka.event.EventStream
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.instance.update.{ InstanceChange, InstanceChangeHandler }
import java.time.Clock

import scala.concurrent.Future

class NotifyOfTaskStateOperationStep(eventBus: EventStream, clock: Clock) extends InstanceChangeHandler {
  override def name: String = "NotifyOfTaskStateOperationStep"

  override def process(instanceChange: InstanceChange): Future[Done] = {
    taskState(instanceChange).foreach { state =>
      val event = TaskStateChangedEvent(
        instanceId = instanceChange.id,
        taskState = state,
        timestamp = clock.instant())
      eventBus.publish(event)
    }

    Future.successful(Done)
  }

  private[this] def taskState(instanceChange: InstanceChange): Option[TaskState] = TaskState(instanceChange.condition)

}

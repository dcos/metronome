package dcos.metronome
package eventbus

import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.task.Task
import org.joda.time.DateTime

case class TaskStateChangedEvent(
  taskId:    Task.Id,
  taskState: TaskState,
  timestamp: DateTime,
  eventType: String    = "task_changed_event"
)

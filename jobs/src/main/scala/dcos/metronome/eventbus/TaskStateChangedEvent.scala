package dcos.metronome
package eventbus

import java.time.Instant

import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.task.Task

case class TaskStateChangedEvent(
  taskId:    Task.Id,
  taskState: TaskState,
  timestamp: Instant,
  eventType: String    = "task_changed_event")

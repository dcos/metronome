package dcos.metronome
package eventbus

import java.time.Instant

import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.instance.Instance
import org.joda.time.DateTime

case class TaskStateChangedEvent(
  instanceId: Instance.Id,
  taskState:  TaskState,
  timestamp:  Instant,
  eventType:  String      = "task_changed_event")

package dcos.metronome
package eventbus

import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.instance.Instance
import org.joda.time.DateTime

case class TaskStateChangedEvent(
  instanceId: Instance.Id,
  taskState:  TaskState,
  timestamp:  DateTime,
  eventType:  String      = "task_changed_event")

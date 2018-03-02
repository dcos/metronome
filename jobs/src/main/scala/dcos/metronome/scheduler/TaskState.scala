package dcos.metronome
package scheduler

import mesosphere.marathon.core.condition.Condition
import mesosphere.marathon.core.task.Task
import mesosphere.mesos.protos.TaskStatus
import org.apache.mesos
/**
  * Internal representation of mesos task states including tasks that have been created but are not yet confirmed.
  */
sealed trait TaskState

object TaskState {

  case object Created extends TaskState
  case object Staging extends TaskState
  case object Starting extends TaskState
  case object Running extends TaskState
  case object Finished extends TaskState
  case object TemporarilyUnreachable extends TaskState // TODO: remove state or handle correctly
  case object Failed extends TaskState
  case object Killed extends TaskState

  val names: Map[String, TaskState] = Map(
    "TASK_CREATED" -> Created,
    "TASK_STAGING" -> Staging,
    "TASK_STARTING" -> Starting,
    "TASK_RUNNING" -> Running,
    "TASK_FINISHED" -> Finished,
    "TASK_FAILED" -> Failed,
    "TASK_KILLED" -> Killed)

  val statusNames: Map[TaskState, String] = names.map{ case (a, b) => (b, a) }

  def name(status: TaskState): String = statusNames(status)
  def unapply(name: String): Option[TaskState] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)

  // FIXME: Task should have a distinct status, always
  // Possible way to achieve this: let the Task have a status
  // and make sure a taskStateChange always provides a task or that status
  def apply(task: Task.LaunchedEphemeral): TaskState = {
    val default: TaskState = TaskState.Created
    apply(task.status.condition).getOrElse(default)
  }

  def apply(condition: Condition): Option[TaskState] = {
    condition match {
      case Condition.Error    => Some(TaskState.Failed)
      case Condition.Failed   => Some(TaskState.Failed)
      case Condition.Finished => Some(TaskState.Finished)
      case Condition.Killed   => Some(TaskState.Killed)
      case Condition.Killing  => Some(TaskState.Running)
      case c if c.isLost      => Some(TaskState.Failed)
      case Condition.Running  => Some(TaskState.Running)
      case Condition.Staging  => Some(TaskState.Staging)
      case Condition.Starting => Some(TaskState.Starting)
      case _                  => None
    }
  }

}

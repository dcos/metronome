package dcos.metronome.scheduler

import mesosphere.marathon.core.task.Task
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
    "TASK_KILLED" -> Killed
  )

  val statusNames: Map[TaskState, String] = names.map{ case (a, b) => (b, a) }

  def name(status: TaskState): String = statusNames(status)
  def unapply(name: String): Option[TaskState] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)

  // FIXME: Task should have a distinct status, always
  // Possible way to achieve this: let the Task have a status
  // and make sure a taskStateChange always provides a task or that status
  def apply(task: Task.LaunchedEphemeral): TaskState = {
    val default: TaskState = TaskState.Created
    task.mesosStatus.fold(default)(apply)
  }

  def apply(mesosTaskStatus: mesos.Protos.TaskStatus): TaskState = {
    import mesos.Protos.{ TaskState => MesosTaskState }
    mesosTaskStatus.getState match {
      case MesosTaskState.TASK_ERROR    => TaskState.Failed
      case MesosTaskState.TASK_FAILED   => TaskState.Failed
      case MesosTaskState.TASK_FINISHED => TaskState.Finished
      case MesosTaskState.TASK_KILLED   => TaskState.Killed
      case MesosTaskState.TASK_KILLING  => TaskState.Running
      case MesosTaskState.TASK_LOST     => TaskState.Failed
      case MesosTaskState.TASK_RUNNING  => TaskState.Running
      case MesosTaskState.TASK_STAGING  => TaskState.Staging
      case MesosTaskState.TASK_STARTING => TaskState.Starting
    }
  }

}
package dcos.metronome
package model

import java.time.Instant

import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.task.Task

import scala.concurrent.duration.Duration

case class JobRun(
    id: JobRunId,
    jobSpec: JobSpec,
    status: JobRunStatus,
    createdAt: Instant,
    completedAt: Option[Instant],
    startingDeadline: Option[Duration],
    tasks: Map[Task.Id, JobRunTask]
)

case class JobRunTask(id: Task.Id, startedAt: Instant, completedAt: Option[Instant], status: TaskState)

object JobRunTask {
  def apply(task: Task): JobRunTask = {
    // Note: Terminal LaunchedEphemeral tasks are expunged from the repo
    // so it is somewhat safe to derive that completedAt for these tasks is always None!
    JobRunTask(
      id = task.taskId,
      startedAt = Instant.ofEpochMilli(task.status.stagedAt.millis),
      completedAt = None,
      status = TaskState(task)
    )
  }
}

sealed trait JobRunStatus
object JobRunStatus {

  /** Initial state of a JobRun to indicate it hasn't been persisted yet */
  case object Initial extends JobRunStatus

  /** JobRun is persisted and tasks have been placed onto the launch queue */
  case object Starting extends JobRunStatus

  /** a task has been reported starting, staging, or running */
  case object Active extends JobRunStatus

  /** a task has been reported finished */
  case object Success extends JobRunStatus

  /** no task has been reported finished and we cannot launch another task */
  case object Failed extends JobRunStatus

  val names: Map[String, JobRunStatus] =
    Map("INITIAL" -> Initial, "STARTING" -> Starting, "ACTIVE" -> Active, "SUCCESS" -> Success, "FAILED" -> Failed)
  val statusNames: Map[JobRunStatus, String] = names.map { case (a, b) => (b, a) }

  def name(status: JobRunStatus): String = statusNames(status)
  def unapply(name: String): Option[JobRunStatus] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}

package dcos.metronome.model

import mesosphere.marathon.core.task.Task
import org.joda.time.DateTime

case class JobRun(
  id:         JobRunId,
  jobSpec:    JobSpec,
  status:     JobRunStatus,
  createdAt:  DateTime,
  finishedAt: Option[DateTime],
  tasks:      Map[Task.Id, JobRunTask]
)

case class JobRunTask(
  id:          Task.Id,
  startedAt:   DateTime,
  completedAt: Option[DateTime],
  status:      String
)

sealed trait JobRunStatus
object JobRunStatus {
  case object Starting extends JobRunStatus
  case object Active extends JobRunStatus
  case object Success extends JobRunStatus
  case object Failed extends JobRunStatus // TODO: we might differentiate between Failed and Aborted

  val names: Map[String, JobRunStatus] = Map(
    "starting" -> Starting,
    "active" -> Active,
    "success" -> Success,
    "failed" -> Failed
  )
  val statusNames: Map[JobRunStatus, String] = names.map{ case (a, b) => (b, a) }

  def name(status: JobRunStatus): String = statusNames(status)
  def unapply(name: String): Option[JobRunStatus] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}


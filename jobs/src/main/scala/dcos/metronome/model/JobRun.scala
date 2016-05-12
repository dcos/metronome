package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.DateTime

case class JobRun(
  id:         String,
  jobId:      PathId,
  status:     JobRunStatus,
  createdAt:  DateTime,
  finishedAt: Option[DateTime],
  tasks:      Seq[JobRunTask]
)

case class JobRunTask(
  id:          String,
  startedAt:   DateTime,
  completedAt: DateTime,
  status:      String
)

sealed trait JobRunStatus
object JobRunStatus {
  case object Active extends JobRunStatus
  case object Success extends JobRunStatus
  case object Failed extends JobRunStatus
}


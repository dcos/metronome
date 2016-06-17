package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTimeZone, DateTime }

case class JobHistory(
  jobSpecId:      PathId,
  successCount:   Long,
  failureCount:   Long,
  lastSuccessAt:  Option[DateTime],
  lastFailureAt:  Option[DateTime],
  successfulRuns: Seq[JobRunInfo],
  failedRuns:     Seq[JobRunInfo]
)

object JobHistory {
  def empty(id: PathId): JobHistory = JobHistory(id, 0, 0, None, None, Seq.empty, Seq.empty)
}

case class JobRunInfo(id: JobRunId, createdAt: DateTime, finishedAt: DateTime)
object JobRunInfo {
  def apply(run: JobRun): JobRunInfo = {
    JobRunInfo(run.id, run.createdAt, DateTime.now(DateTimeZone.UTC))
  }
}

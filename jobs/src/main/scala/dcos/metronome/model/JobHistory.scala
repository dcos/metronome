package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTimeZone, DateTime }

case class JobHistory(
  id:                     PathId,
  successCount:           Int,
  failureCount:           Int,
  lastSuccessAt:          Option[DateTime],
  lastFailureAt:          Option[DateTime],
  successfulFinishedRuns: Seq[RunInfo],
  failedFinishedRuns:     Seq[RunInfo]
)

object JobHistory {
  def empty(id: PathId): JobHistory = JobHistory(id, 0, 0, None, None, Seq.empty, Seq.empty)
}

case class RunInfo(id: JobRunId, createdAt: DateTime, finishedAt: DateTime)
object RunInfo {
  def apply(run: JobRun): RunInfo = {
    RunInfo(run.id, run.createdAt, run.finishedAt.getOrElse(DateTime.now(DateTimeZone.UTC)))
  }
}

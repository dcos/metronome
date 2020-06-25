package dcos.metronome
package model

import java.time.{Clock, Instant}

import mesosphere.marathon.core.task.Task

case class JobHistorySummary(
    jobSpecId: JobId,
    successCount: Long,
    failureCount: Long,
    lastSuccessAt: Option[Instant],
    lastFailureAt: Option[Instant]
)
object JobHistorySummary {
  def apply(h: JobHistory): JobHistorySummary = {
    JobHistorySummary(h.jobSpecId, h.successCount, h.failureCount, h.lastSuccessAt, h.lastFailureAt)
  }
  def empty(id: JobId): JobHistorySummary = JobHistorySummary(id, 0, 0, None, None)
}

case class JobHistory(
    jobSpecId: JobId,
    successCount: Long,
    failureCount: Long,
    lastSuccessAt: Option[Instant],
    lastFailureAt: Option[Instant],
    successfulRuns: Seq[JobRunInfo],
    failedRuns: Seq[JobRunInfo]
)

object JobHistory {
  def empty(id: JobId): JobHistory = JobHistory(id, 0, 0, None, None, Seq.empty, Seq.empty)
}

case class JobRunInfo(id: JobRunId, createdAt: Instant, finishedAt: Instant, tasks: Seq[Task.Id])
object JobRunInfo {
  def apply(run: JobRun): JobRunInfo = {
    JobRunInfo(run.id, run.createdAt, Clock.systemUTC().instant(), run.tasks.keys.to[Seq])
  }
}

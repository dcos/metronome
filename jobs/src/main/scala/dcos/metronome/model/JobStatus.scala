package dcos.metronome.model

import org.joda.time.DateTime

case class JobStatus(
  successCount:           Int,
  failureCount:           Int,
  lastSuccessAt:          Option[DateTime],
  lastFailureAt:          Option[DateTime],
  activeRuns:             Seq[String],
  successfulFinishedRuns: Seq[String],
  failedFinishedRuns:     Seq[String]
)


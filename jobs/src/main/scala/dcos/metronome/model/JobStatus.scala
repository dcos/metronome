package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.DateTime

case class JobStatus(
  jobSpecId:     PathId,
  successCount:  Long,
  failureCount:  Long,
  lastSuccessAt: Option[DateTime],
  lastFailureAt: Option[DateTime],
  activeRuns:    Seq[String]
)


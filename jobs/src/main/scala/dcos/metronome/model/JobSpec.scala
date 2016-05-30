package dcos.metronome.model

import mesosphere.marathon.state.PathId

case class JobSpec(
  id:          PathId,
  description: String,
  labels:      Map[String, String]  = JobSpec.DefaultLabels,
  schedule:    Option[ScheduleSpec] = JobSpec.DefaultSchedule,
  run:         RunSpec              = JobSpec.DefaultRunSpec
)

object JobSpec {
  val DefaultLabels = Map.empty[String, String]
  val DefaultSchedule = None
  val DefaultRunSpec = RunSpec()
}


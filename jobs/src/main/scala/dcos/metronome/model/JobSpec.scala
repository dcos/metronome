package dcos.metronome.model

import mesosphere.marathon.state.PathId

case class JobSpec(
  id: PathId,
  labels: Map[String, String],
  schedule: Option[ScheduleSpec],
  run: RunSpec)

object JobSpec {
  val DefaultLabels = Map.empty
}


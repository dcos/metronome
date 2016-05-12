package dcos.metronome.model

import scala.concurrent.duration.Duration

case class RestartSpec(
  policy:         RestartPolicy    = RestartSpec.DefaultRestartPolicy,
  activeDeadline: Option[Duration] = RestartSpec.DefaultActiveDeadline
)

object RestartSpec {
  val DefaultRestartPolicy = RestartNever
  val DefaultActiveDeadline = None
}


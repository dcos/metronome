package dcos.metronome
package model

import scala.concurrent.duration.Duration

case class RestartSpec(
    policy: RestartPolicy = RestartSpec.DefaultRestartPolicy,
    activeDeadline: Option[Duration] = RestartSpec.DefaultActiveDeadline
)

object RestartSpec {
  val DefaultRestartPolicy = RestartPolicy.Never
  val DefaultActiveDeadline = None
}

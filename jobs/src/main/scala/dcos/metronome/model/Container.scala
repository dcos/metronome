package dcos.metronome.model

import mesosphere.marathon.state.Parameter
import scala.collection.immutable.Seq

trait Container

case class DockerSpec(
  image:          String,
  privileged:     Boolean        = false,
  parameters:     Seq[Parameter] = Nil,
  forcePullImage: Boolean        = false
) extends Container

object DockerSpec {
  val DefaultParameters = Seq.empty[Parameter]
}

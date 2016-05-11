package dcos.metronome.model

import mesosphere.marathon.state.Parameter

trait Container

case class DockerSpec(
  image: String,
  network: String = DockerSpec.DefaultNetwork,
  privileged: Boolean = DockerSpec.DefaultPrivileged,
  parameters: Seq[Parameter] = DockerSpec.DefaultParameter,
  forcePullImage: Boolean = DockerSpec.DefaultForcePullImage) extends Container

object DockerSpec {

  val DefaultNetwork = "HOST"
  val DefaultParameter = Seq.empty[Parameter]
  val DefaultPrivileged = false
  val DefaultForcePullImage = false

}


package dcos.metronome
package model

import mesosphere.marathon.state.Parameter
import scala.collection.immutable.Seq

trait Container

case class DockerSpec(
  image:          String,
  privileged:     Boolean        = false,
  parameters:     Seq[Parameter] = Nil,
  forcePullImage: Boolean        = false) extends Container

object DockerSpec {
  val DefaultParameters = Seq.empty[Parameter]
}

case class ImageSpec(
  id:         String,
  kind:       String                   = ImageSpec.DefaultKind,
  forcePull:  Boolean                  = false,
  pullConfig: Option[DockerPullConfig] = Option.empty[DockerPullConfig])

object ImageSpec {
  val DefaultKind = "docker"
}

case class UcrSpec(
  image:      ImageSpec,
  privileged: Boolean   = false) extends Container

case class DockerPullConfig(secret: String)

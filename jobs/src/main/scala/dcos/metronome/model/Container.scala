package dcos.metronome.model

trait Container

case class DockerSpec(image: String, forcePullImage: Option[Boolean] = Some(false)) extends Container


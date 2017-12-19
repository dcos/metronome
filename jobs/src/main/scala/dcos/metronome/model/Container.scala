package dcos.metronome.model

trait Container

case class DockerSpec(image: String, forcePullImage: Boolean = false) extends Container


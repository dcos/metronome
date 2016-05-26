package dcos.metronome.model

trait Container

case class DockerSpec(image: String) extends Container


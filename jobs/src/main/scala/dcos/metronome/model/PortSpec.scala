package dcos.metronome.model

case class PortSpec(
  protocol: String,
  name: String,
  hostPort: Option[Int],
  containerPort: Option[Int],
  labels: Map[String, String])


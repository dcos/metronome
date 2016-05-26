package dcos.metronome.model

import org.apache.mesos.{ Protos => Mesos }

case class Volume(
  containerPath: String,
  hostPath:      String,
  mode:          Mesos.Volume.Mode
)


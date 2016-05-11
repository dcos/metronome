package dcos.metronome.model

import mesosphere.marathon.state.Volume

import scala.concurrent.duration._

case class Artifact(uri: String, extract: Boolean = true, executable: Boolean = false, cache: Boolean = false)

case class RunSpec(
  cpus: Double = RunSpec.DefaultCpus,
  mem: Double = RunSpec.DefaultMem,
  disk: Double = RunSpec.DefaultDisk,
  cmd: Option[String] = RunSpec.DefaultCmd,
  args: Option[Seq[String]] = RunSpec.DefaultArgs,
  user: Option[String] = RunSpec.DefaultUser,
  env: Map[String, String] = RunSpec.DefaultEnv,
  placement: PlacementSpec = RunSpec.DefaultPlacement,
  artifacts: Seq[Artifact] = RunSpec.DefaultArtifacts,
  maxLaunchDelay: Duration = RunSpec.DefaultMaxLaunchDelay,
  docker: Option[DockerSpec] = RunSpec.DefaultDocker,
  ports: Seq[PortSpec] = RunSpec.DefaultPorts,
  volumes: Seq[Volume] = RunSpec.DefaultVolumes,
  restartPolicy: RestartPolicy = RunSpec.DefaultRestartPolicy,
  activeDeadline: Duration = RunSpec.DefaultActiveDeadline)

object RunSpec {
  val DefaultCpus: Double = 1.0
  val DefaultMem: Double = 128.0
  val DefaultDisk: Double = 0.0
  val DefaultPlacement = PlacementSpec(Seq.empty)
  val DefaultMaxLaunchDelay = 1.hour
  val DefaultCmd = None
  val DefaultArgs = None
  val DefaultUser = None
  val DefaultEnv = Map.empty[String, String]
  val DefaultArtifacts = Seq.empty[Artifact]
  val DefaultDocker = None
  val DefaultPorts = Seq.empty[PortSpec]
  val DefaultVolumes = Seq.empty[Volume]
  val DefaultRestartPolicy = RestartNever
  val DefaultActiveDeadline = 10.minutes
}


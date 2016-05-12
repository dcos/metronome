package dcos.metronome.model

import scala.concurrent.duration._
import scala.collection.immutable._

case class Artifact(uri: String, extract: Boolean = true, executable: Boolean = false, cache: Boolean = false)

case class RunSpec(
  cpus:           Double              = RunSpec.DefaultCpus,
  mem:            Double              = RunSpec.DefaultMem,
  disk:           Double              = RunSpec.DefaultDisk,
  cmd:            Option[String]      = RunSpec.DefaultCmd,
  args:           Option[Seq[String]] = RunSpec.DefaultArgs,
  user:           Option[String]      = RunSpec.DefaultUser,
  env:            Map[String, String] = RunSpec.DefaultEnv,
  placement:      PlacementSpec       = RunSpec.DefaultPlacement,
  artifacts:      Seq[Artifact]       = RunSpec.DefaultArtifacts,
  maxLaunchDelay: Duration            = RunSpec.DefaultMaxLaunchDelay,
  docker:         Option[DockerSpec]  = RunSpec.DefaultDocker,
  volumes:        Seq[Volume]         = RunSpec.DefaultVolumes,
  restart:        RestartSpec         = RunSpec.DefaultRestartSpec
)

object RunSpec {
  val DefaultCpus: Double = 1.0
  val DefaultMem: Double = 128.0
  val DefaultDisk: Double = 0.0
  val DefaultPlacement = PlacementSpec()
  val DefaultMaxLaunchDelay = 1.hour
  val DefaultCmd = None
  val DefaultArgs = None
  val DefaultUser = None
  val DefaultEnv = Map.empty[String, String]
  val DefaultArtifacts = Seq.empty[Artifact]
  val DefaultDocker = None
  val DefaultVolumes = Seq.empty[Volume]
  val DefaultRestartSpec = RestartSpec()
}


package dcos.metronome
package model

import com.wix.accord.dsl._
import com.wix.accord.{ RuleViolation, Success, Validator }
import com.wix.accord.dsl.{ size, valid, validator }
import mesosphere.marathon.api.v2.Validation.every

import scala.concurrent.duration._
import scala.collection.immutable._

case class Artifact(uri: String, extract: Boolean = true, executable: Boolean = false, cache: Boolean = false)

case class JobRunSpec(
  cpus:                       Double                 = JobRunSpec.DefaultCpus,
  mem:                        Double                 = JobRunSpec.DefaultMem,
  disk:                       Double                 = JobRunSpec.DefaultDisk,
  cmd:                        Option[String]         = JobRunSpec.DefaultCmd,
  args:                       Option[Seq[String]]    = JobRunSpec.DefaultArgs,
  user:                       Option[String]         = JobRunSpec.DefaultUser,
  env:                        Map[String, String]    = JobRunSpec.DefaultEnv,
  placement:                  PlacementSpec          = JobRunSpec.DefaultPlacement,
  artifacts:                  Seq[Artifact]          = JobRunSpec.DefaultArtifacts,
  maxLaunchDelay:             Duration               = JobRunSpec.DefaultMaxLaunchDelay,
  docker:                     Option[DockerSpec]     = JobRunSpec.DefaultDocker,
  volumes:                    Seq[Volume]            = JobRunSpec.DefaultVolumes,
  restart:                    RestartSpec            = JobRunSpec.DefaultRestartSpec,
  taskKillGracePeriodSeconds: Option[FiniteDuration] = JobRunSpec.DefaultTaskKillGracePeriodSeconds)

object JobRunSpec {
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
  val DefaultTaskKillGracePeriodSeconds = None

  implicit lazy val validJobRunSpec: Validator[JobRunSpec] = new Validator[JobRunSpec] {
    import com.wix.accord._
    import ViolationBuilder._

    override def apply(jobRunSpec: JobRunSpec): Result = {
      if (jobRunSpec.cmd.isDefined || jobRunSpec.docker.exists(d => d.image.nonEmpty))
        Success
      else
        RuleViolation(jobRunSpec, JobRunSpecMessages.cmdOrDockerValidation, None)
    }
  }
}

object JobRunSpecMessages {
  val cmdOrDockerValidation = "Cmd or docker image must be specified"
}

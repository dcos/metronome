package dcos.metronome
package model

import com.wix.accord.Validator

import scala.concurrent.duration._

case class Artifact(uri: String, extract: Boolean = true, executable: Boolean = false, cache: Boolean = false)

case class JobRunSpec(
  cpus:                       Double                           = JobRunSpec.DefaultCpus,
  mem:                        Double                           = JobRunSpec.DefaultMem,
  disk:                       Double                           = JobRunSpec.DefaultDisk,
  cmd:                        Option[String]                   = JobRunSpec.DefaultCmd,
  args:                       Option[Seq[String]]              = JobRunSpec.DefaultArgs,
  user:                       Option[String]                   = JobRunSpec.DefaultUser,
  env:                        Map[String, EnvVarValueOrSecret] = JobRunSpec.DefaultEnv,
  placement:                  PlacementSpec                    = JobRunSpec.DefaultPlacement,
  artifacts:                  Seq[Artifact]                    = JobRunSpec.DefaultArtifacts,
  maxLaunchDelay:             Duration                         = JobRunSpec.DefaultMaxLaunchDelay,
  docker:                     Option[DockerSpec]               = JobRunSpec.DefaultDocker,
  volumes:                    Seq[Volume]                      = JobRunSpec.DefaultVolumes,
  restart:                    RestartSpec                      = JobRunSpec.DefaultRestartSpec,
  taskKillGracePeriodSeconds: Option[FiniteDuration]           = JobRunSpec.DefaultTaskKillGracePeriodSeconds,
  secrets:                    Map[String, SecretDef]           = JobRunSpec.DefaultSecrets)

object JobRunSpec {
  val DefaultCpus: Double = 1.0
  val DefaultMem: Double = 128.0
  val DefaultDisk: Double = 0.0
  val DefaultPlacement = PlacementSpec()
  val DefaultMaxLaunchDelay = 1.hour
  val DefaultCmd = None
  val DefaultArgs = None
  val DefaultUser = None
  val DefaultEnv = Map.empty[String, EnvVarValueOrSecret]
  val DefaultArtifacts = Seq.empty[Artifact]
  val DefaultDocker = None
  val DefaultVolumes = Seq.empty[Volume]
  val DefaultRestartSpec = RestartSpec()
  val DefaultTaskKillGracePeriodSeconds = None
  val DefaultSecrets = Map.empty[String, SecretDef]

  implicit lazy val validJobRunSpec: Validator[JobRunSpec] = new Validator[JobRunSpec] {
    import com.wix.accord._
    import ViolationBuilder._

    override def apply(jobRunSpec: JobRunSpec): Result = {
      var violations = Set.empty[Result]

      def check(test: Boolean, errorMessage: String) = {
        if (!test) {
          violations += RuleViolation(jobRunSpec, errorMessage)
        }
      }
      val envVarDefinedSecretNames = jobRunSpec.env.values.collect { case EnvVarSecret(secretName) => secretName }.toSet
      val providedSecretNames = jobRunSpec.secrets.keySet

      check(jobRunSpec.cmd.isDefined || jobRunSpec.docker.exists(d => d.image.nonEmpty), JobRunSpecMessages.cmdOrDockerValidation)
      check(envVarDefinedSecretNames == providedSecretNames, JobRunSpecMessages.secretsValidation(envVarDefinedSecretNames, providedSecretNames))

      violations.headOption.getOrElse(Success)
    }
  }
}

object JobRunSpecMessages {
  val cmdOrDockerValidation = "Cmd or docker image must be specified"
  def secretsValidation(envVarSecretsName: Set[String], providedSecretsNames: Set[String]) = {
    s"Secret names are different from provided secrets. Defined: ${envVarSecretsName.mkString(", ")}, Provided: ${providedSecretsNames.mkString(", ")}"
  }
}

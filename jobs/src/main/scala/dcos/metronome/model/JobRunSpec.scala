package dcos.metronome
package model

import dcos.metronome.model.Network.NetworkMode

import scala.concurrent.duration._

case class Artifact(uri: String, extract: Boolean = true, executable: Boolean = false, cache: Boolean = false)

case class JobRunSpec(
    cpus: Double = JobRunSpec.DefaultCpus,
    mem: Double = JobRunSpec.DefaultMem,
    disk: Double = JobRunSpec.DefaultDisk,
    gpus: Int = JobRunSpec.DefaultGpus,
    cmd: Option[String] = JobRunSpec.DefaultCmd,
    args: Option[Seq[String]] = JobRunSpec.DefaultArgs,
    user: Option[String] = JobRunSpec.DefaultUser,
    env: Map[String, EnvVarValueOrSecret] = JobRunSpec.DefaultEnv,
    placement: PlacementSpec = JobRunSpec.DefaultPlacement,
    artifacts: Seq[Artifact] = JobRunSpec.DefaultArtifacts,
    maxLaunchDelay: Duration = JobRunSpec.DefaultMaxLaunchDelay,
    docker: Option[DockerSpec] = JobRunSpec.DefaultDocker,
    ucr: Option[UcrSpec] = JobRunSpec.DefaultUcr,
    volumes: Seq[Volume] = JobRunSpec.DefaultVolumes,
    restart: RestartSpec = JobRunSpec.DefaultRestartSpec,
    taskKillGracePeriodSeconds: Option[FiniteDuration] = JobRunSpec.DefaultTaskKillGracePeriodSeconds,
    secrets: Map[String, SecretDef] = JobRunSpec.DefaultSecrets,
    networks: Seq[Network] = JobRunSpec.DefaultNetworks
)

object JobRunSpec {
  val DefaultCpus: Double = 1.0
  val DefaultMem: Double = 128.0
  val DefaultDisk: Double = 0.0
  val DefaultGpus: Int = 0
  val DefaultPlacement = PlacementSpec()
  val DefaultMaxLaunchDelay = 1.hour
  val DefaultCmd = None
  val DefaultArgs = None
  val DefaultUser = None
  val DefaultEnv = Map.empty[String, EnvVarValueOrSecret]
  val DefaultArtifacts = Seq.empty[Artifact]
  val DefaultDocker = None
  val DefaultUcr = None
  val DefaultNetworks = Seq.empty[Network]
  val DefaultVolumes = Seq.empty[Volume]
  val DefaultRestartSpec = RestartSpec()
  val DefaultTaskKillGracePeriodSeconds = None
  val DefaultSecrets = Map.empty[String, SecretDef]

  import com.wix.accord.dsl._
  import mesosphere.marathon.api.v2.Validation.isTrue
  import ValidationHelpers.genericValidator

  private def networkingValidation(isUcr: Boolean, isDocker: Boolean) =
    genericValidator[Seq[Network]] { networks =>
      var errors = List.empty[String]
      val networkModes = networks.iterator.map(_.mode).toSet
      val isContainerLess = !(isUcr || isDocker)
      val networkNames = networks.iterator.flatMap(_.name).toSeq.sorted

      if (networkModes.size > 1)
        errors = JobRunSpecMessages.mixedNetworkModesNotAllowed :: errors

      if (isContainerLess) {
        if (networkModes.exists(_ != NetworkMode.Host))
          errors = JobRunSpecMessages.onlyContainersMayJoinContainerOrBridge :: errors
      } else if (isDocker) {
        if ((networkModes == Set(NetworkMode.Container)) && (networks.size > 1))
          errors = JobRunSpecMessages.dockerDoesNotAllowSpecificationOfMultipleNetworks :: errors
      }

      if ((networkModes.exists(_ != NetworkMode.Container)) && (networks.size > 1)) {
        errors = JobRunSpecMessages.onlyOneHostOrBridgeNetworkDefinitionAllowed :: errors
      }

      if (networkNames != networkNames.distinct)
        errors = JobRunSpecMessages.networkNamesMustBeUnique :: errors

      errors
    }

  private val jobRunSpecHasSomethingToRun = isTrue[JobRunSpec](JobRunSpecMessages.cmdOrDockerValidation) { jobRunSpec =>
    jobRunSpec.cmd.nonEmpty || jobRunSpec.docker.exists(d => d.image.nonEmpty) || jobRunSpec.ucr.nonEmpty
  }

  private val jobRunSpecOnlyHasOneThingToRun = isTrue[JobRunSpec](JobRunSpecMessages.onlyDockerOrUcr) { jobRunSpec =>
    !(jobRunSpec.docker.nonEmpty && jobRunSpec.ucr.nonEmpty)
  }

  private val allSecretsAreDefinedAndUsed = genericValidator[JobRunSpec] { (jobRunSpec) =>
    val referencedSecretNames =
      jobRunSpec.env.values.collect { case EnvVarSecret(secretName) => secretName }.toSet ++
        jobRunSpec.volumes.collect { case SecretVolume(_, secret) => secret }.toSet
    val providedSecretNames = jobRunSpec.secrets.keySet

    val undefinedSecrets = referencedSecretNames.diff(providedSecretNames)
    val unusedSecrets = providedSecretNames.diff(referencedSecretNames)

    if (!(undefinedSecrets.isEmpty && unusedSecrets.isEmpty))
      List(JobRunSpecMessages.secretsValidation(undefinedSecrets, unusedSecrets))
    else
      Nil
  }

  implicit val validJobRunSpec = validator[JobRunSpec] { jobRunSpec =>
    jobRunSpec.networks is networkingValidation(jobRunSpec.ucr.nonEmpty, jobRunSpec.docker.nonEmpty)

    jobRunSpec.gpus is isTrue[Int](JobRunSpecMessages.gpusNotValidWithDocker) { gpus =>
      gpus == 0 || jobRunSpec.docker.isEmpty
    }

    jobRunSpec.volumes is isTrue[Seq[Volume]](JobRunSpecMessages.fileBasedSecretsAreUcrOnly) { vols =>
      jobRunSpec.docker.isEmpty || jobRunSpec.volumes.forall { v => !v.isInstanceOf[SecretVolume] }
    }

    jobRunSpec.networks.each is (Network.validNetworkDefinition)
  }.and(jobRunSpecHasSomethingToRun)
    .and(jobRunSpecOnlyHasOneThingToRun)
    .and(allSecretsAreDefinedAndUsed)
}

object JobRunSpecMessages {
  val cmdOrDockerValidation = "Cmd, Docker or UCR image must be specified"

  def secretsValidation(undefinedSecrets: Set[String], unusedSecrets: Set[String]) = {
    val sb = StringBuilder.newBuilder
    if (undefinedSecrets.nonEmpty)
      sb.append("The following secrets are referenced, but undefined: ")
        .append(undefinedSecrets.mkString(", "))
        .append(". Please add them to the secrets field.")
    if (unusedSecrets.nonEmpty)
      sb.append("The following secrets are defined, but not referenced: ")
        .append(unusedSecrets.mkString(", "))
        .append(". Please remove them from the secrets field.")
    sb.mkString
  }

  val onlyDockerOrUcr = "Either Docker or UCR should be provided, but not both"
  val fileBasedSecretsAreUcrOnly = "File based secrets are only supported by UCR"
  val gpusNotValidWithDocker = "GPUs are not supported with Docker"

  val mixedNetworkModesNotAllowed =
    "You may not mix host, container, or container/bridge networking. Only define one kind of network."
  val onlyOneHostOrBridgeNetworkDefinitionAllowed = "Only one host or container/bridge network modes are allowed"
  val dockerDoesNotAllowSpecificationOfMultipleNetworks = "Docker does not allow specification of multiple networks"
  val onlyContainersMayJoinContainerOrBridge = "Only containers may join container or bridge networks"
  val networkNamesMustBeUnique = "network names must be unique"
}

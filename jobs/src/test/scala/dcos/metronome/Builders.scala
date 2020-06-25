package dcos.metronome

import dcos.metronome.model.{
  Artifact,
  DockerSpec,
  EnvVarValueOrSecret,
  ImageSpec,
  JobRunSpec,
  Network,
  PlacementSpec,
  RestartSpec,
  SecretDef,
  UcrSpec,
  Volume
}

import scala.concurrent.duration.{Duration, FiniteDuration}

object Builders {
  def newImage(id: String = "image", forcePull: Boolean = false): ImageSpec = ImageSpec(id = id, forcePull = forcePull)

  object newJobRunSpec {
    def docker(
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
        docker: Option[DockerSpec] = Some(newDockerSpec()),
        volumes: Seq[Volume] = JobRunSpec.DefaultVolumes,
        restart: RestartSpec = JobRunSpec.DefaultRestartSpec,
        taskKillGracePeriodSeconds: Option[FiniteDuration] = JobRunSpec.DefaultTaskKillGracePeriodSeconds,
        secrets: Map[String, SecretDef] = JobRunSpec.DefaultSecrets,
        networks: Seq[Network] = JobRunSpec.DefaultNetworks
    ): JobRunSpec = {
      JobRunSpec(
        cpus = cpus,
        mem = mem,
        disk = disk,
        gpus = gpus,
        cmd = cmd,
        args = args,
        user = user,
        env = env,
        placement = placement,
        artifacts = artifacts,
        maxLaunchDelay = maxLaunchDelay,
        docker = docker,
        ucr = None,
        volumes = volumes,
        restart = restart,
        taskKillGracePeriodSeconds = taskKillGracePeriodSeconds,
        secrets = secrets,
        networks = networks
      )
    }

    def ucr(
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
        ucr: Option[UcrSpec] = Some(newUcrSpec()),
        volumes: Seq[Volume] = JobRunSpec.DefaultVolumes,
        restart: RestartSpec = JobRunSpec.DefaultRestartSpec,
        taskKillGracePeriodSeconds: Option[FiniteDuration] = JobRunSpec.DefaultTaskKillGracePeriodSeconds,
        secrets: Map[String, SecretDef] = JobRunSpec.DefaultSecrets,
        networks: Seq[Network] = JobRunSpec.DefaultNetworks
    ): JobRunSpec = {
      JobRunSpec(
        cpus = cpus,
        mem = mem,
        disk = disk,
        gpus = gpus,
        cmd = cmd,
        args = args,
        user = user,
        env = env,
        placement = placement,
        artifacts = artifacts,
        maxLaunchDelay = maxLaunchDelay,
        docker = None,
        ucr = ucr,
        volumes = volumes,
        restart = restart,
        taskKillGracePeriodSeconds = taskKillGracePeriodSeconds,
        secrets = secrets,
        networks = networks
      )
    }

    def command(
        cpus: Double = JobRunSpec.DefaultCpus,
        mem: Double = JobRunSpec.DefaultMem,
        disk: Double = JobRunSpec.DefaultDisk,
        gpus: Int = JobRunSpec.DefaultGpus,
        cmd: String = "sleep 3600",
        args: Option[Seq[String]] = JobRunSpec.DefaultArgs,
        user: Option[String] = JobRunSpec.DefaultUser,
        env: Map[String, EnvVarValueOrSecret] = JobRunSpec.DefaultEnv,
        placement: PlacementSpec = JobRunSpec.DefaultPlacement,
        artifacts: Seq[Artifact] = JobRunSpec.DefaultArtifacts,
        maxLaunchDelay: Duration = JobRunSpec.DefaultMaxLaunchDelay,
        volumes: Seq[Volume] = JobRunSpec.DefaultVolumes,
        restart: RestartSpec = JobRunSpec.DefaultRestartSpec,
        taskKillGracePeriodSeconds: Option[FiniteDuration] = JobRunSpec.DefaultTaskKillGracePeriodSeconds,
        secrets: Map[String, SecretDef] = JobRunSpec.DefaultSecrets,
        networks: Seq[Network] = JobRunSpec.DefaultNetworks
    ) = {
      JobRunSpec(
        cpus = cpus,
        mem = mem,
        disk = disk,
        gpus = gpus,
        cmd = Some(cmd),
        args = args,
        user = user,
        env = env,
        placement = placement,
        artifacts = artifacts,
        maxLaunchDelay = maxLaunchDelay,
        docker = None,
        ucr = None,
        volumes = volumes,
        restart = restart,
        taskKillGracePeriodSeconds = taskKillGracePeriodSeconds,
        secrets = secrets,
        networks = networks
      )
    }
  }

  object newNetwork {
    def apply(
        name: Option[String] = None,
        mode: Network.NetworkMode = Network.NetworkMode.Host,
        labels: Map[String, String] = Map.empty
    ): Network = {
      Network(name, mode, labels)
    }

    def host(): Network = {
      apply(None, Network.NetworkMode.Host, Map.empty)
    }

    def bridge(labels: Map[String, String] = Map.empty): Network = {
      apply(None, Network.NetworkMode.ContainerBridge, labels)
    }

    def container(name: String = "name", labels: Map[String, String] = Map.empty): Network = {
      apply(Some(name), Network.NetworkMode.Container, labels)
    }
  }

  def newUcrSpec(image: ImageSpec = newImage()): UcrSpec =
    UcrSpec(image)

  def newDockerSpec(image: String = "image"): DockerSpec =
    DockerSpec(image)
}

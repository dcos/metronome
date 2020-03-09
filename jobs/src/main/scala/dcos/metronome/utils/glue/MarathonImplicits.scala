package dcos.metronome
package utils.glue

import java.util.concurrent.TimeUnit

import dcos.metronome.model._
import mesosphere.marathon
import mesosphere.marathon.core.health.HealthCheck
import mesosphere.marathon.core.pod
import mesosphere.marathon.core.readiness.ReadinessCheck
import mesosphere.marathon.raml.Resources
import mesosphere.marathon.state.{ AppDefinition, BackoffStrategy, Container, FetchUri, PathId, PortDefinition, RunSpec, UpgradeStrategy, VersionInfo, VolumeMount }
import mesosphere.marathon.state
import scala.concurrent.duration._

/**
  * Temporary object containing implicit conversions to and from Marathon code. Should be removed eventually.
  */
object MarathonImplicits {
  import org.apache.mesos
  implicit class ModelToVolumeMode(val mode: Mode) extends AnyVal {
    def toProto: mesos.Protos.Volume.Mode = mode match {
      case Mode.RO => mesos.Protos.Volume.Mode.RO
      case Mode.RW => mesos.Protos.Volume.Mode.RW
    }
  }

  implicit class ModelToVolume(val volume: Volume) extends AnyVal {
    def toMarathon: mesosphere.marathon.state.VolumeWithMount[state.Volume] = volume match {
      case HostVolume(containerPath, hostPath, mode) =>
        mesosphere.marathon.state.VolumeWithMount(
          volume = state.HostVolume(None, hostPath),
          mount = VolumeMount(None, containerPath, mode == Mode.RO))

      case SecretVolume(containerPath, secret) =>
        mesosphere.marathon.state.VolumeWithMount(
          volume = state.SecretVolume(None, secret),
          mount = VolumeMount(None, containerPath))
    }
  }

  implicit class ArtifactToFetchUri(val artifact: Artifact) extends AnyVal {
    def toFetchUri: FetchUri = FetchUri(
      uri = artifact.uri,
      extract = artifact.extract,
      executable = artifact.executable,
      cache = artifact.cache)
  }

  implicit class ConstraintSpecToProto(val spec: ConstraintSpec) extends AnyVal {
    def toProto: Option[marathon.Protos.Constraint] = {
      /**
        * Unfortunately, Metronome has always allowed valueless constraint operators, but they never had any effect.
        *
        * For the sake of consistency with Marathon, the Eq operator was replaced with Is. Previously, Eq mapped to
        * CLUSTER, and valueless CLUSTER constraint (which has a specific meaning in Marathon) has no meaning in the
        * context of jobs.
        *
        * Ideally, we would make the value required, but this would be an API breaking change.
        */
      spec.value.map { value =>
        val marathonOperator = spec.operator match {
          case Operator.Is     => marathon.Protos.Constraint.Operator.IS
          case Operator.Like   => marathon.Protos.Constraint.Operator.LIKE
          case Operator.Unlike => marathon.Protos.Constraint.Operator.UNLIKE
        }

        val builder = marathon.Protos.Constraint.newBuilder()
          .setOperator(marathonOperator)
          .setField(spec.attribute)
        builder.setValue(value)
        builder.build()
      }
    }
  }

  implicit class JobRunIdToRunSpecId(val jobRunId: JobRunId) extends AnyVal {
    // TODO: should we remove JobRunId.toPathId?
    def toRunSpecId: PathId = jobRunId.toPathId
  }

  implicit class JobSpecToContainer(val jobSpec: JobSpec) extends AnyVal {
    def toContainer: Option[Container] = {
      require(!(jobSpec.run.docker.nonEmpty && jobSpec.run.ucr.nonEmpty), "docker and ucr can't be present both")
      val maybeDocker = jobSpec.run.docker.map { dockerSpec =>
        Container.Docker(
          image = dockerSpec.image,
          volumes = jobSpec.run.volumes.map(_.toMarathon),
          forcePullImage = dockerSpec.forcePullImage,
          privileged = dockerSpec.privileged,
          parameters = dockerSpec.parameters)
      }

      val maybeUcr = jobSpec.run.ucr.map { ucrSpec =>
        Container.MesosDocker(
          image = ucrSpec.image.id,
          volumes = jobSpec.run.volumes.map(_.toMarathon),
          forcePullImage = ucrSpec.image.forcePull) // TODO: pass privileged once marathon will support it
      }

      maybeDocker.orElse(maybeUcr)
    }
  }

  implicit class JobRunToRunSpec(val run: JobRun) extends AnyVal {
    def toRunSpec: RunSpec = {
      val jobSpec = run.jobSpec

      AppDefinition(
        id = run.id.toRunSpecId,
        cmd = jobSpec.run.cmd,
        args = jobSpec.run.args.getOrElse(Seq.empty),
        user = jobSpec.run.user,
        env = MarathonConversions.envVarToMarathon(jobSpec.run.env),
        instances = 1,
        resources = Resources(cpus = jobSpec.run.cpus, mem = jobSpec.run.mem, disk = jobSpec.run.disk, gpus = jobSpec.run.gpus),
        executor = "//cmd",
        constraints = jobSpec.run.placement.constraints.flatMap(spec => spec.toProto)(collection.breakOut),
        fetch = jobSpec.run.artifacts.map(_.toFetchUri),
        portDefinitions = Seq.empty[PortDefinition],
        requirePorts = false,
        backoffStrategy = BackoffStrategy(
          backoff = 0.seconds,
          factor = 0.0,
          maxLaunchDelay = FiniteDuration(jobSpec.run.maxLaunchDelay.toMillis, TimeUnit.MILLISECONDS)),
        container = jobSpec.toContainer,
        healthChecks = Set.empty[HealthCheck],
        readinessChecks = Seq.empty[ReadinessCheck],
        taskKillGracePeriod = jobSpec.run.taskKillGracePeriodSeconds,
        dependencies = Set.empty[PathId],
        upgradeStrategy = UpgradeStrategy(minimumHealthCapacity = 0.0, maximumOverCapacity = 1.0),
        labels = jobSpec.labels,
        acceptedResourceRoles = Set.empty,
        versionInfo = VersionInfo.NoVersion,
        secrets = MarathonConversions.secretsToMarathon(jobSpec.run.secrets),
        networks = convertNetworks)
    }

    private def convertNetworks: Seq[pod.Network] = {
      run.jobSpec.networks.map {
        case n: pod.Network => n
        case o =>
          throw new IllegalStateException(s"s${o} is an illegal state and should not have been permitted by validation")
      }
    }
  }
}

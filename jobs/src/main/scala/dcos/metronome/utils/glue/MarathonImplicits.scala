package dcos.metronome.utils.glue

import java.util.concurrent.TimeUnit

import dcos.metronome.model._
import dcos.metronome.scheduler.SchedulerConfig
import mesosphere.marathon
import mesosphere.marathon.core.health.HealthCheck
import mesosphere.marathon.core.readiness.ReadinessCheck
import mesosphere.marathon.state.{ AppDefinition, Container, EnvVarValue, FetchUri, PathId, PortDefinition, RunSpec, Secret, UpgradeStrategy }

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.language.implicitConversions

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
    def toMarathon: mesosphere.marathon.state.Volume = mesosphere.marathon.state.Volume(
      containerPath = volume.containerPath,
      hostPath = Some(volume.hostPath),
      mode = volume.mode.toProto,
      persistent = None,
      external = None
    )
  }

  implicit class ArtifactToFetchUri(val artifact: Artifact) extends AnyVal {
    def toFetchUri: FetchUri = FetchUri(
      uri = artifact.uri,
      extract = artifact.extract,
      executable = artifact.executable,
      cache = artifact.cache
    )
  }

  implicit class ConstraintSpecToProto(val spec: ConstraintSpec) extends AnyVal {
    def toProto: marathon.Protos.Constraint = {
      val marathonOperator = spec.operator match {
        case Operator.Eq     => marathon.Protos.Constraint.Operator.CLUSTER
        case Operator.Like   => marathon.Protos.Constraint.Operator.LIKE
        case Operator.Unlike => marathon.Protos.Constraint.Operator.UNLIKE
      }

      val builder = marathon.Protos.Constraint.newBuilder()
        .setOperator(marathonOperator)
        .setField(spec.attribute)
      spec.value.foreach(builder.setValue)
      builder.build()
    }
  }

  implicit class JobRunIdToRunSpecId(val jobRunId: JobRunId) extends AnyVal {
    // TODO: should we remove JobRunId.toPathId?
    def toRunSpecId: PathId = jobRunId.toPathId
  }

  implicit class JobSpecToContainer(val jobSpec: JobSpec) extends AnyVal {
    def toContainer: Option[Container] = {
      jobSpec.run.docker match {
        case Some(dockerSpec) => Some(Container.Docker(
          image = dockerSpec.image,
          volumes = jobSpec.run.volumes.map(_.toMarathon),
          forcePullImage = dockerSpec.forcePullImage
        ))
        case _ => None
      }

    }
  }

  implicit class JobRunToRunSpec(val run: JobRun) extends AnyVal {
    def toRunSpec: RunSpec = {
      val jobSpec = run.jobSpec

      // TODO: do we need a metronome-specific RunSpec implementation?
      AppDefinition(
        id = run.id.toRunSpecId,
        cmd = jobSpec.run.cmd,
        args = jobSpec.run.args,
        user = jobSpec.run.user,
        env = EnvVarValue(jobSpec.run.env),
        instances = 1,
        cpus = jobSpec.run.cpus,
        mem = jobSpec.run.mem,
        disk = jobSpec.run.disk,
        executor = "//cmd",
        constraints = jobSpec.run.placement.constraints.map(spec => spec.toProto).toSet,
        fetch = jobSpec.run.artifacts.map(_.toFetchUri),
        storeUrls = Seq.empty[String],
        portDefinitions = Seq.empty[PortDefinition],
        requirePorts = false,
        backoff = 0.seconds,
        backoffFactor = 0.0,
        maxLaunchDelay = FiniteDuration(jobSpec.run.maxLaunchDelay.toMillis, TimeUnit.MILLISECONDS),
        container = jobSpec.toContainer,
        healthChecks = Set.empty[HealthCheck],
        readinessChecks = Seq.empty[ReadinessCheck],
        taskKillGracePeriod = jobSpec.run.taskKillGracePeriodSeconds,
        dependencies = Set.empty[PathId],
        upgradeStrategy = UpgradeStrategy(minimumHealthCapacity = 0.0, maximumOverCapacity = 1.0),
        labels = jobSpec.labels,
        acceptedResourceRoles = None,
        ipAddress = None,
        versionInfo = AppDefinition.VersionInfo.NoVersion,
        residency = None,
        secrets = Map.empty[String, Secret]
      )
    }
  }
}
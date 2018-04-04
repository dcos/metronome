package dcos.metronome
package repository.impl.kv.marshaller

import java.time.ZoneId

import dcos.metronome.model._
import dcos.metronome.repository.impl.kv.EntityMarshaller
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable

object JobSpecMarshaller extends EntityMarshaller[JobSpec] {
  val log = LoggerFactory.getLogger(JobSpecMarshaller.getClass)

  override def toBytes(jobSpec: JobSpec): IndexedSeq[Byte] = {
    import JobSpecConversions.JobSpecToProto

    jobSpec.toProto.toByteArray.to[IndexedSeq]
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobSpec] =
    safeConversion { fromProto(Protos.JobSpec.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobSpec): JobSpec = {
    import JobSpecConversions.ProtoToJobSpec

    proto.toModel
  }
}

object JobSpecConversions {
  implicit class JobSpecToProto(val jobSpec: JobSpec) extends AnyVal {
    def toProto: Protos.JobSpec = {
      import RunSpecConversions.RunSpecToProto

      val builder = Protos.JobSpec.newBuilder()

      builder
        .setId(jobSpec.id.toString)
        .addAllLabels(jobSpec.labels.toProto.asJava)
        .setRun(jobSpec.run.toProto)
        .addAllSchedules(jobSpec.schedules.toProto.asJava)

      jobSpec.description.foreach(builder.setDescription)

      builder.build()
    }
  }

  implicit class ProtoToJobSpec(val proto: Protos.JobSpec) extends AnyVal {
    def toModel: JobSpec = {
      import RunSpecConversions.ProtoToRunSpec

      val description = if (proto.hasDescription) Some(proto.getDescription) else None

      JobSpec(
        id = JobId(proto.getId),
        description = description,
        labels = proto.getLabelsList.asScala.toModel,
        schedules = proto.getSchedulesList.asScala.toModel,
        run = proto.getRun.toModel)
    }
  }

  implicit class LabelsToProto(val labels: Map[String, String]) extends AnyVal {
    def toProto: Iterable[Protos.Label] = labels.map {
      case (key, value) =>
        Protos.Label.newBuilder()
          .setKey(key)
          .setValue(value)
          .build
    }
  }

  implicit class ProtoToLabels(val labels: mutable.Buffer[Protos.Label]) extends AnyVal {
    def toModel: Map[String, String] = labels.map { label =>
      label.getKey -> label.getValue
    }.toMap
  }

  implicit class ScheduleSpecsToProto(val schedules: Seq[ScheduleSpec]) extends AnyVal {
    def toProto: Seq[Protos.JobSpec.ScheduleSpec] = schedules.map { schedule =>
      Protos.JobSpec.ScheduleSpec.newBuilder()
        .setId(schedule.id)
        .setSchedule(schedule.cron.toString)
        .setTz(schedule.timeZone.toString)
        .setStartingDeadline(schedule.startingDeadline.toSeconds)
        .setConcurrencyPolicy(
          Protos.JobSpec.ScheduleSpec.ConcurrencyPolicy.valueOf(
            ConcurrencyPolicy.name(schedule.concurrencyPolicy)))
        .setEnabled(schedule.enabled)
        .build
    }
  }

  implicit class ProtoToScheduleSpec(val schedules: mutable.Buffer[Protos.JobSpec.ScheduleSpec]) extends AnyVal {
    def toModel: Seq[ScheduleSpec] = {
      import scala.concurrent.duration._

      schedules.map { schedule =>
        ScheduleSpec(
          id = schedule.getId,
          cron = CronSpec(schedule.getSchedule),
          timeZone = ZoneId.of(schedule.getTz),
          startingDeadline = schedule.getStartingDeadline.seconds,
          concurrencyPolicy = ConcurrencyPolicy.names(schedule.getConcurrencyPolicy.toString),
          enabled = schedule.getEnabled)
      }.toList
    }
  }
}

object RunSpecConversions {
  implicit class RunSpecToProto(val runSpec: JobRunSpec) extends AnyVal {
    def toProto: Protos.JobSpec.RunSpec = {
      val builder = Protos.JobSpec.RunSpec.newBuilder()

      builder
        .setCpus(runSpec.cpus)
        .setMem(runSpec.mem)
        .setDisk(runSpec.disk)
        .setMaxLaunchDelay(runSpec.maxLaunchDelay.toSeconds)
        .setPlacement(runSpec.placement.toProto)
        .setRestart(runSpec.restart.toProto)
        .addAllEnvironment(runSpec.env.toEnvProto.asJava)
        .addAllEnvironmentSecrets(runSpec.env.toEnvSecretProto.asJava)
        .addAllArtifacts(runSpec.artifacts.toProto.asJava)
        .addAllVolumes(runSpec.volumes.toProto.asJava)
        .addAllSecrets(runSpec.secrets.toProto.asJava)

      runSpec.cmd.foreach(builder.setCmd)
      runSpec.args.foreach { args => builder.addAllArguments(args.asJava) }
      runSpec.user.foreach(builder.setUser)
      runSpec.docker.foreach { docker => builder.setDocker(docker.toProto) }
      runSpec.taskKillGracePeriodSeconds.foreach { killGracePeriod => builder.setTaskKillGracePeriodSeconds(killGracePeriod.toSeconds) }

      builder.build()
    }
  }

  implicit class ProtoToRunSpec(val runSpec: Protos.JobSpec.RunSpec) extends AnyVal {
    def toModel: JobRunSpec = {
      import scala.concurrent.duration._

      val cmd = if (runSpec.hasCmd) Some(runSpec.getCmd) else None
      val args = if (runSpec.getArgumentsCount == 0) None else Some(runSpec.getArgumentsList.asScala.toList)
      val user = if (runSpec.hasUser) Some(runSpec.getUser) else None
      val docker = if (runSpec.hasDocker) Some(runSpec.getDocker.toModel) else None
      val taskKillGracePeriodSeconds = if (runSpec.hasTaskKillGracePeriodSeconds) Some(Duration(runSpec.getTaskKillGracePeriodSeconds, SECONDS)) else None

      JobRunSpec(
        cpus = runSpec.getCpus,
        mem = runSpec.getMem,
        disk = runSpec.getDisk,
        maxLaunchDelay = runSpec.getMaxLaunchDelay.seconds,
        placement = runSpec.getPlacement.toModel,
        restart = runSpec.getRestart.toModel,
        env = runSpec.getEnvironmentList.asScala.toModel ++ runSpec.getEnvironmentSecretsList.asScala.toModel,
        artifacts = runSpec.getArtifactsList.asScala.toModel,
        volumes = runSpec.getVolumesList.asScala.toModel,
        cmd = cmd,
        args = args,
        user = user,
        docker = docker,
        taskKillGracePeriodSeconds = taskKillGracePeriodSeconds,
        secrets = runSpec.getSecretsList.asScala.toModel)
    }
  }

  implicit class RestartSpecToProto(val restart: RestartSpec) extends AnyVal {
    def toProto: Protos.JobSpec.RunSpec.RestartSpec = {
      val builder = Protos.JobSpec.RunSpec.RestartSpec.newBuilder

      builder.setPolicy(
        Protos.JobSpec.RunSpec.RestartSpec.RestartPolicy.valueOf(RestartPolicy.name(restart.policy)))

      restart.activeDeadline.foreach { activeDeadline => builder.setActiveDeadline(activeDeadline.toSeconds) }

      builder.build
    }
  }

  implicit class ProtoToRestartSpec(val restart: Protos.JobSpec.RunSpec.RestartSpec) extends AnyVal {
    def toModel: RestartSpec = {
      import scala.concurrent.duration._

      val activeDeadline = if (restart.hasActiveDeadline) Some(restart.getActiveDeadline.seconds) else None

      RestartSpec(policy = RestartPolicy.names(restart.getPolicy.toString), activeDeadline = activeDeadline)
    }
  }

  implicit class VolumesToProto(val volumes: Seq[Volume]) extends AnyVal {
    def toProto: Iterable[Protos.JobSpec.RunSpec.Volume] = volumes.map { volume =>
      Protos.JobSpec.RunSpec.Volume.newBuilder()
        .setContainerPath(volume.containerPath)
        .setHostPath(volume.hostPath)
        .setMode(
          Protos.JobSpec.RunSpec.Volume.Mode.valueOf(Mode.name(volume.mode)))
        .build
    }
  }

  implicit class ProtoToVolumes(val volumes: mutable.Buffer[Protos.JobSpec.RunSpec.Volume]) extends AnyVal {
    def toModel: Seq[Volume] = volumes.map { volume =>
      Volume(
        containerPath = volume.getContainerPath,
        hostPath = volume.getHostPath,
        mode = Mode.names(volume.getMode.toString))
    }.toList
  }

  implicit class PlacementSpecToProto(val placement: PlacementSpec) extends AnyVal {
    def toProto: Protos.JobSpec.RunSpec.PlacementSpec = {
      Protos.JobSpec.RunSpec.PlacementSpec.newBuilder()
        .addAllConstraints(placement.constraints.toProto.asJava)
        .build()
    }
  }

  implicit class ProtoToPlacementSpec(val placementSpec: Protos.JobSpec.RunSpec.PlacementSpec) extends AnyVal {
    def toModel: PlacementSpec = PlacementSpec(constraints = placementSpec.getConstraintsList.asScala.toModel)
  }

  implicit class ConstraintsToProto(val constraints: Seq[ConstraintSpec]) extends AnyVal {
    def toProto: Iterable[Protos.JobSpec.RunSpec.PlacementSpec.Constraint] = constraints.map { constraint =>
      val builder = Protos.JobSpec.RunSpec.PlacementSpec.Constraint.newBuilder

      constraint.value.foreach(builder.setValue)

      builder
        .setAttribute(constraint.attribute)
        .setOperator(
          Protos.JobSpec.RunSpec.PlacementSpec.Constraint.Operator.valueOf(Operator.name(constraint.operator)))
        .build()
    }
  }

  implicit class ProtosToConstraintSpec(val constraints: mutable.Buffer[Protos.JobSpec.RunSpec.PlacementSpec.Constraint]) extends AnyVal {
    def toModel: Seq[ConstraintSpec] = constraints.map { constraint =>
      val value = if (constraint.hasValue) Some(constraint.getValue) else None
      ConstraintSpec(
        attribute = constraint.getAttribute,
        operator = Operator.names(constraint.getOperator.toString),
        value = value)
    }.toList
  }

  implicit class ArtifactsToProto(val artifacts: Seq[Artifact]) extends AnyVal {
    def toProto: Iterable[Protos.JobSpec.RunSpec.Artifact] = artifacts.map { artifact =>
      Protos.JobSpec.RunSpec.Artifact.newBuilder()
        .setUrl(artifact.uri)
        .setExtract(artifact.extract)
        .setExecutable(artifact.executable)
        .setCache(artifact.cache)
        .build()
    }
  }

  implicit class ProtosToArtifacts(val artifacts: mutable.Buffer[Protos.JobSpec.RunSpec.Artifact]) extends AnyVal {
    def toModel: Seq[Artifact] = artifacts.map { artifact =>
      Artifact(
        uri = artifact.getUrl,
        extract = artifact.getExtract,
        executable = artifact.getExecutable,
        cache = artifact.getCache)
    }.toList
  }

  implicit class DockerSpecToProto(val dockerSpec: DockerSpec) extends AnyVal {
    def toProto: Protos.JobSpec.RunSpec.DockerSpec = {
      Protos.JobSpec.RunSpec.DockerSpec.newBuilder().setImage(dockerSpec.image).setForcePullImage(dockerSpec.forcePullImage).build()
    }
  }

  implicit class ProtoToDockerSpec(val dockerSpec: Protos.JobSpec.RunSpec.DockerSpec) extends AnyVal {
    def toModel: DockerSpec = DockerSpec(image = dockerSpec.getImage, forcePullImage = dockerSpec.getForcePullImage)
  }

  implicit class EnvironmentToProto(val environment: Map[String, EnvVarValueOrSecret]) extends AnyVal {
    def toEnvProto: Iterable[Protos.JobSpec.RunSpec.EnvironmentVariable] = environment.collect {
      case (key, EnvVarValue(value)) =>
        Protos.JobSpec.RunSpec.EnvironmentVariable.newBuilder()
          .setKey(key)
          .setValue(value)
          .build
    }
    def toEnvSecretProto: Iterable[Protos.JobSpec.RunSpec.EnvironmentVariableSecret] = environment.collect {
      case (name, EnvVarSecret(secretId)) =>
        Protos.JobSpec.RunSpec.EnvironmentVariableSecret.newBuilder()
          .setName(name)
          .setSecretId(secretId)
          .build
    }
  }

  implicit class SecretsToProto(val secrets: Map[String, SecretDef]) extends AnyVal {
    def toProto: Iterable[Protos.JobSpec.RunSpec.Secret] = secrets.map {
      case (secretId, SecretDef(source)) =>
        Protos.JobSpec.RunSpec.Secret.newBuilder()
          .setId(secretId)
          .setSource(source)
          .build()
    }
  }

  implicit class ProtosToEnvironment(val environmentVariables: mutable.Buffer[Protos.JobSpec.RunSpec.EnvironmentVariable]) extends AnyVal {
    def toModel: Map[String, EnvVarValueOrSecret] = environmentVariables.map { environmentVariable =>
      environmentVariable.getKey -> EnvVarValue(environmentVariable.getValue)
    }.toMap
  }

  implicit class ProtosToEnvironmentSecrets(val environmentSecrets: mutable.Buffer[Protos.JobSpec.RunSpec.EnvironmentVariableSecret]) extends AnyVal {
    def toModel: Map[String, EnvVarValueOrSecret] = environmentSecrets.map { environmentSecret =>
      environmentSecret.getName -> EnvVarSecret(environmentSecret.getSecretId)
    }.toMap
  }

  implicit class ProtosToSecrets(val secrets: mutable.Buffer[Protos.JobSpec.RunSpec.Secret]) extends AnyVal {
    def toModel: Map[String, SecretDef] = secrets.map { secret =>
      secret.getId -> SecretDef(secret.getSource)
    }.toMap
  }

}

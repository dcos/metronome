package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model._
import dcos.metronome.repository.impl.kv.EntityMarshaller
import mesosphere.marathon.state.PathId
import org.joda.time.DateTimeZone
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

import scala.collection.immutable.Seq
import scala.collection.mutable.Buffer

object JobSpecMarshaller extends EntityMarshaller[JobSpec] {
  val log = LoggerFactory.getLogger(JobSpecMarshaller.getClass)

  override def toBytes(jobSpec: JobSpec): IndexedSeq[Byte] = {
    import JobSpecConversions.JobSpecToProto

    jobSpec.toProto.toByteArray
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobSpec] =
    safeConversion { fromProto(Protos.JobSpec.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobSpec): JobSpec = {
    import JobSpecConversions.ProtoToJobSpec

    proto.toModel
  }
}

object JobSpecConversions {
  implicit class JobSpecToProto(jobSpec: JobSpec) {
    def toProto: Protos.JobSpec = {
      import RunSpecConversions.RunSpecToProto

      val builder = Protos.JobSpec.newBuilder()

      builder
        .setId(jobSpec.id.toString())
        .addAllLabels(jobSpec.labels.toProto.asJava)
        .setRun(jobSpec.run.toProto)
        .addAllSchedules(jobSpec.schedules.toProto.asJava)

      jobSpec.description.foreach(builder.setDescription)

      builder.build()
    }
  }

  implicit class ProtoToJobSpec(proto: Protos.JobSpec) {
    def toModel: JobSpec = {
      import RunSpecConversions.ProtoToRunSpec

      val description = if (proto.hasDescription) Some(proto.getDescription) else None

      JobSpec(
        id = PathId(proto.getId),
        description = description,
        labels = proto.getLabelsList.asScala.toModel,
        schedules = proto.getSchedulesList.asScala.toModel,
        run = proto.getRun.toModel
      )
    }
  }

  implicit class LabelsToProto(labels: Map[String, String]) {
    def toProto: Iterable[Protos.Label] = labels.map {
      case (key, value) =>
        Protos.Label.newBuilder()
          .setKey(key)
          .setValue(value)
          .build
    }
  }

  implicit class ProtoToLabels(labels: Buffer[Protos.Label]) {
    def toModel: Map[String, String] = labels.map { label =>
      label.getKey -> label.getValue
    }.toMap
  }

  implicit class ScheduleSpecsToProto(schedules: Seq[ScheduleSpec]) {
    def toProto: Seq[Protos.JobSpec.ScheduleSpec] = schedules.map { schedule =>
      Protos.JobSpec.ScheduleSpec.newBuilder()
        .setId(schedule.id)
        .setSchedule(schedule.cron.toString)
        .setTz(schedule.timeZone.toString)
        .setStartingDeadline(schedule.startingDeadline.toSeconds)
        .setConcurrencyPolicy(
          Protos.JobSpec.ScheduleSpec.ConcurrencyPolicy.valueOf(
            ConcurrencyPolicy.name(schedule.concurrencyPolicy)
          )
        )
        .setEnabled(schedule.enabled)
        .build
    }
  }

  implicit class ProtoToScheduleSpec(schedules: Buffer[Protos.JobSpec.ScheduleSpec]) {
    def toModel: Seq[ScheduleSpec] = {
      import scala.concurrent.duration._

      schedules.map { schedule =>
        ScheduleSpec(
          id = schedule.getId,
          cron = CronSpec(schedule.getSchedule),
          timeZone = DateTimeZone.forID(schedule.getTz),
          startingDeadline = schedule.getStartingDeadline.seconds,
          concurrencyPolicy = ConcurrencyPolicy.names(schedule.getConcurrencyPolicy.toString),
          enabled = schedule.getEnabled
        )
      }.toList
    }
  }
}

object RunSpecConversions {
  implicit class RunSpecToProto(runSpec: JobRunSpec) {
    def toProto: Protos.JobSpec.RunSpec = {
      val builder = Protos.JobSpec.RunSpec.newBuilder()

      builder
        .setCpus(runSpec.cpus)
        .setMem(runSpec.mem)
        .setDisk(runSpec.disk)
        .setMaxLaunchDelay(runSpec.maxLaunchDelay.toSeconds)
        .setPlacement(runSpec.placement.toProto)
        .setRestart(runSpec.restart.toProto)
        .addAllEnvironment(runSpec.env.toProto.asJava)
        .addAllArtifacts(runSpec.artifacts.toProto.asJava)
        .addAllVolumes(runSpec.volumes.toProto.asJava)

      runSpec.cmd.foreach(builder.setCmd)
      runSpec.args.foreach { args => builder.addAllArguments(args.asJava) }
      runSpec.user.foreach(builder.setUser)
      runSpec.docker.foreach { docker => builder.setDocker(docker.toProto) }

      builder.build()
    }
  }

  implicit class ProtoToRunSpec(runSpec: Protos.JobSpec.RunSpec) {
    def toModel: JobRunSpec = {
      import scala.concurrent.duration._

      val cmd = if (runSpec.hasCmd) Some(runSpec.getCmd) else None
      val args = if (runSpec.getArgumentsCount == 0) None else Some(runSpec.getArgumentsList.asScala.toList)
      val user = if (runSpec.hasUser) Some(runSpec.getUser) else None
      val docker = if (runSpec.hasDocker) Some(runSpec.getDocker.toModel) else None

      JobRunSpec(
        cpus = runSpec.getCpus,
        mem = runSpec.getMem,
        disk = runSpec.getDisk,
        maxLaunchDelay = runSpec.getMaxLaunchDelay.seconds,
        placement = runSpec.getPlacement.toModel,
        restart = runSpec.getRestart.toModel,
        env = runSpec.getEnvironmentList.asScala.toModel,
        artifacts = runSpec.getArtifactsList.asScala.toModel,
        volumes = runSpec.getVolumesList.asScala.toModel,
        cmd = cmd,
        args = args,
        user = user,
        docker = docker
      )
    }
  }

  implicit class RestartSpecToProto(restart: RestartSpec) {
    def toProto: Protos.JobSpec.RunSpec.RestartSpec = {
      val builder = Protos.JobSpec.RunSpec.RestartSpec.newBuilder

      builder.setPolicy(
        Protos.JobSpec.RunSpec.RestartSpec.RestartPolicy.valueOf(RestartPolicy.name(restart.policy))
      )

      restart.activeDeadline.foreach { activeDeadline => builder.setActiveDeadline(activeDeadline.toSeconds) }

      builder.build
    }
  }

  implicit class ProtoToRestartSpec(restart: Protos.JobSpec.RunSpec.RestartSpec) {
    def toModel: RestartSpec = {
      import scala.concurrent.duration._

      val activeDeadline = if (restart.hasActiveDeadline) Some(restart.getActiveDeadline.seconds) else None

      RestartSpec(policy = RestartPolicy.names(restart.getPolicy.toString), activeDeadline = activeDeadline)
    }
  }

  implicit class VolumesToProto(volumes: Seq[Volume]) {
    def toProto: Iterable[Protos.JobSpec.RunSpec.Volume] = volumes.map { volume =>
      Protos.JobSpec.RunSpec.Volume.newBuilder()
        .setContainerPath(volume.containerPath)
        .setHostPath(volume.hostPath)
        .setMode(
          Protos.JobSpec.RunSpec.Volume.Mode.valueOf(Mode.name(volume.mode))
        )
        .build
    }
  }

  implicit class ProtoToVolumes(volumes: Buffer[Protos.JobSpec.RunSpec.Volume]) {
    def toModel: Seq[Volume] = volumes.map { volume =>
      Volume(
        containerPath = volume.getContainerPath,
        hostPath = volume.getHostPath,
        mode = Mode.names(volume.getMode.toString)
      )
    }.toList
  }

  implicit class PlacementSpecToProto(placement: PlacementSpec) {
    def toProto: Protos.JobSpec.RunSpec.PlacementSpec = {
      Protos.JobSpec.RunSpec.PlacementSpec.newBuilder()
        .addAllConstraints(placement.constraints.toProto.asJava)
        .build()
    }
  }

  implicit class ProtoToPlacementSpec(placementSpec: Protos.JobSpec.RunSpec.PlacementSpec) {
    def toModel: PlacementSpec = PlacementSpec(constraints = placementSpec.getConstraintsList.asScala.toModel)
  }

  implicit class ConstraintsToProto(constraints: Seq[ConstraintSpec]) {
    def toProto: Iterable[Protos.JobSpec.RunSpec.PlacementSpec.Constraint] = constraints.map { constraint =>
      val builder = Protos.JobSpec.RunSpec.PlacementSpec.Constraint.newBuilder

      constraint.value.foreach(builder.setValue)

      builder
        .setAttribute(constraint.attribute)
        .setOperator(
          Protos.JobSpec.RunSpec.PlacementSpec.Constraint.Operator.valueOf(Operator.name(constraint.operator))
        )
        .build()
    }
  }

  implicit class ProtosToConstraintSpec(constraints: Buffer[Protos.JobSpec.RunSpec.PlacementSpec.Constraint]) {
    def toModel: Seq[ConstraintSpec] = constraints.map { constraint =>
      val value = if (constraint.hasValue) Some(constraint.getValue) else None
      ConstraintSpec(
        attribute = constraint.getAttribute,
        operator = Operator.names(constraint.getOperator.toString),
        value = value
      )
    }.toList
  }

  implicit class ArtifactsToProto(artifacts: Seq[Artifact]) {
    def toProto: Iterable[Protos.JobSpec.RunSpec.Artifact] = artifacts.map { artifact =>
      Protos.JobSpec.RunSpec.Artifact.newBuilder()
        .setUrl(artifact.uri)
        .setExtract(artifact.extract)
        .setExecutable(artifact.executable)
        .setCache(artifact.cache)
        .build()
    }
  }

  implicit class ProtosToArtifacts(artifacts: Buffer[Protos.JobSpec.RunSpec.Artifact]) {
    def toModel: Seq[Artifact] = artifacts.map { artifact =>
      Artifact(
        uri = artifact.getUrl,
        extract = artifact.getExtract,
        executable = artifact.getExecutable,
        cache = artifact.getCache
      )
    }.toList
  }

  implicit class DockerSpecToProto(dockerSpec: DockerSpec) {
    def toProto: Protos.JobSpec.RunSpec.DockerSpec = {
      Protos.JobSpec.RunSpec.DockerSpec.newBuilder().setImage(dockerSpec.image).build()
    }
  }

  implicit class ProtoToDockerSpec(dockerSpec: Protos.JobSpec.RunSpec.DockerSpec) {
    def toModel: DockerSpec = DockerSpec(image = dockerSpec.getImage)
  }

  implicit class EnvironmentToProto(environment: Map[String, String]) {
    def toProto: Iterable[Protos.JobSpec.RunSpec.EnvironmentVariable] = environment.map {
      case (key, value) =>
        Protos.JobSpec.RunSpec.EnvironmentVariable.newBuilder()
          .setKey(key)
          .setValue(value)
          .build
    }
  }

  implicit class ProtosToEnvironment(environmentVariables: Buffer[Protos.JobSpec.RunSpec.EnvironmentVariable]) {
    def toModel: Map[String, String] = environmentVariables.map { environmentVariable =>
      environmentVariable.getKey -> environmentVariable.getValue
    }.toMap
  }

}

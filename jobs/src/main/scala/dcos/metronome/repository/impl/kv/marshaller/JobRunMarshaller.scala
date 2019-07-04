package dcos.metronome
package repository.impl.kv.marshaller

import java.time.Instant
import java.util.concurrent.TimeUnit

import dcos.metronome.model._
import dcos.metronome.repository.impl.kv.EntityMarshaller
import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.task.Task
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

object JobRunMarshaller extends EntityMarshaller[JobRun] {
  val log = LoggerFactory.getLogger(JobRunMarshaller.getClass)

  override def toBytes(jobRun: JobRun): IndexedSeq[Byte] = {
    import JobRunConversions.JobRunToProto

    jobRun.toProto.toByteArray.to[IndexedSeq]
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobRun] =
    safeConversion { fromProto(Protos.JobRun.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobRun): JobRun = {
    import JobRunConversions.ProtoToJobRun

    proto.toModel
  }
}

object JobRunConversions {
  import PlacementMarshaller._
  import EnvironmentMarshaller._

  implicit class JobRunIdToProto(val jobRunId: JobRunId) extends AnyVal {
    def toProto: Protos.JobRun.Id = {
      Protos.JobRun.Id.newBuilder()
        .setJobId(jobRunId.jobId.toString)
        .setRunId(jobRunId.value)
        .build()
    }
  }

  implicit class JobRunTaskToProto(val task: JobRunTask) extends AnyVal {
    def toProto: Protos.JobRun.JobRunTask = {
      val builder = Protos.JobRun.JobRunTask.newBuilder()
        .setId(task.id.idString)
        .setStartedAt(task.startedAt.toEpochMilli)
        .setStatus(task.status.toProto)
      task.completedAt.foreach(t => builder.setCompletedAt(t.toEpochMilli))
      builder.build()
    }
  }

  implicit class ProtoToJobRunTask(val proto: Protos.JobRun.JobRunTask) extends AnyVal {
    def toModel: JobRunTask = JobRunTask(
      Task.Id(proto.getId),
      Instant.ofEpochMilli(proto.getStartedAt),
      if (proto.hasCompletedAt) Some(Instant.ofEpochMilli(proto.getCompletedAt)) else None,
      proto.getStatus.toModel)
  }

  implicit class ProtoToJobRunId(val proto: Protos.JobRun.Id) extends AnyVal {
    def toModel: JobRunId = JobRunId(JobId(proto.getJobId), proto.getRunId)
  }

  implicit class JobRunToProto(val jobRun: JobRun) extends AnyVal {
    def toProto: Protos.JobRun = {
      import JobSpecConversions.JobSpecToProto

      val builder = Protos.JobRun.newBuilder()
        .setId(jobRun.id.toProto)
        .setJobSpec(jobRun.jobSpec.toProto)
        .setStatus(jobRun.status.toProto)
        .setCreatedAt(jobRun.createdAt.toEpochMilli)

      jobRun.completedAt.foreach(date => builder.setFinishedAt(date.toEpochMilli))
      jobRun.tasks.values.foreach(task => builder.addTasks(task.toProto))
      jobRun.startingDeadline.foreach(d => builder.setStartingDeadlineSeconds(d.toSeconds))
      builder.build()
    }
  }

  implicit class ProtoToJobRun(val proto: Protos.JobRun) extends AnyVal {
    def toModel: JobRun = {
      import JobSpecConversions.ProtoToJobSpec

      JobRun(
        id = proto.getId.toModel,
        jobSpec = proto.getJobSpec.toModel,
        overrides = if (proto.hasOverrides) proto.getOverrides.toModel else JobRunSpecOverrides.empty,
        status = proto.getStatus.toModel,
        createdAt = Instant.ofEpochMilli(proto.getCreatedAt),
        completedAt = if (proto.hasFinishedAt) Some(Instant.ofEpochMilli(proto.getFinishedAt)) else None,
        startingDeadline = if (proto.hasStartingDeadlineSeconds)
          Some(Duration(proto.getStartingDeadlineSeconds, TimeUnit.SECONDS))
        else
          None,
        tasks = proto.getTasksList.asScala.map(_.toModel).map(task => task.id -> task).toMap)
    }
  }

  implicit class JobRunStatusToProto(val status: JobRunStatus) extends AnyVal {
    def toProto: Protos.JobRun.Status = Protos.JobRun.Status.valueOf(JobRunStatus.name(status))
  }

  implicit class ProtoToJobRunStatus(val proto: Protos.JobRun.Status) extends AnyVal {
    def toModel: JobRunStatus = JobRunStatus.names(proto.toString)
  }

  implicit class TaskStateToProto(val taskState: TaskState) extends AnyVal {
    def toProto: Protos.JobRun.JobRunTask.Status = Protos.JobRun.JobRunTask.Status.valueOf(TaskState.name(taskState))
  }

  implicit class ProtoToTaskState(val proto: Protos.JobRun.JobRunTask.Status) extends AnyVal {
    def toModel: TaskState = TaskState.names(proto.toString)
  }

  implicit class JobRunSpecOverridesToProto(val overrides: JobRunSpecOverrides) extends AnyVal {
    def toProto: Protos.JobRun.JobRunSpecOverrides = Protos.JobRun.JobRunSpecOverrides.newBuilder()
      .addAllEnvironment(overrides.env.toEnvProto.asJava)
      .setPlacement(overrides.placement.toProto)
      .build()
  }

  implicit class ProtoToJobRunSpecOverrides(val proto: Protos.JobRun.JobRunSpecOverrides) extends AnyVal {
    def toModel: JobRunSpecOverrides = JobRunSpecOverrides(
      env = proto.getEnvironmentList.asScala.toModel,
      placement = proto.getPlacement.toModel)
  }
}

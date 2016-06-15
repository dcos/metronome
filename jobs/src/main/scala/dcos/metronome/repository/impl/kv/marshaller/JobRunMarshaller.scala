package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model.{ JobRunTask, JobRun, JobRunId, JobRunStatus }
import dcos.metronome.repository.impl.kv.EntityMarshaller
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTime, DateTimeZone }
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

object JobRunMarshaller extends EntityMarshaller[JobRun] {
  val log = LoggerFactory.getLogger(JobRunMarshaller.getClass)

  override def toBytes(jobRun: JobRun): IndexedSeq[Byte] = {
    import JobRunConversions.JobRunToProto

    jobRun.toProto.toByteArray
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobRun] =
    safeConversion { fromProto(Protos.JobRun.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobRun): JobRun = {
    import JobRunConversions.ProtoToJobRun

    proto.toModel
  }
}

object JobRunConversions {
  implicit class JobRunIdToProto(jobRunId: JobRunId) {
    def toProto: Protos.JobRun.Id = {
      Protos.JobRun.Id.newBuilder()
        .setJobId(jobRunId.jobId.toString)
        .setRunId(jobRunId.value)
        .build()
    }
  }

  implicit class JobRunTaskToProto(task: JobRunTask) {
    def toProto: Protos.JobRun.JobRunTask = {
      val builder = Protos.JobRun.JobRunTask.newBuilder()
        .setId(task.id.idString)
        .setStartedAt(task.startedAt.getMillis)
        .setStatus(task.status)
      task.completedAt.foreach(t => builder.setCompletedAt(t.getMillis))
      builder.build()
    }
  }

  implicit class ProtoToJobRunTask(proto: Protos.JobRun.JobRunTask) {
    def toModel: JobRunTask = JobRunTask(
      Task.Id(proto.getId),
      new DateTime(proto.getStartedAt, DateTimeZone.UTC),
      if (proto.hasCompletedAt) Some(new DateTime(proto.getCompletedAt, DateTimeZone.UTC)) else None,
      proto.getStatus
    )
  }

  implicit class ProtoToJobRunId(proto: Protos.JobRun.Id) {
    def toModel: JobRunId = JobRunId(PathId(proto.getJobId), proto.getRunId)
  }

  implicit class JobRunToProto(jobRun: JobRun) {
    def toProto: Protos.JobRun = {
      import JobSpecConversions.JobSpecToProto

      val builder = Protos.JobRun.newBuilder()
        .setId(jobRun.id.toProto)
        .setJobSpec(jobRun.jobSpec.toProto)
        .setStatus(jobRun.status.toProto)
        .setCreatedAt(jobRun.createdAt.getMillis)

      jobRun.finishedAt.foreach(date => builder.setFinishedAt(date.getMillis))
      jobRun.tasks.values.foreach(task => builder.addTasks(task.toProto))
      builder.build()
    }
  }

  implicit class ProtoToJobRun(proto: Protos.JobRun) {
    def toModel: JobRun = {
      import JobSpecConversions.ProtoToJobSpec

      JobRun(
        id = proto.getId.toModel,
        jobSpec = proto.getJobSpec.toModel,
        status = proto.getStatus.toModel,
        createdAt = new DateTime(proto.getCreatedAt, DateTimeZone.UTC),
        finishedAt = if (proto.hasFinishedAt) Some(new DateTime(proto.getFinishedAt, DateTimeZone.UTC)) else None,
        tasks = proto.getTasksList.asScala.map(_.toModel).map(task => task.id -> task).toMap
      )
    }
  }

  implicit class JobRunStatusToProto(status: JobRunStatus) {
    def toProto: Protos.JobRun.Status = Protos.JobRun.Status.valueOf(JobRunStatus.name(status))
  }

  implicit class ProtoToJobRunStatus(proto: Protos.JobRun.Status) {
    def toModel: JobRunStatus = JobRunStatus.names(proto.toString)
  }
}
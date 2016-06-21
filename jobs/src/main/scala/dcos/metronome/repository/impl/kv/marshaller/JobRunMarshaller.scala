package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model._
import dcos.metronome.repository.impl.kv.EntityMarshaller
import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.task.Task
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
        .setStartedAt(task.startedAt.getMillis)
        .setState(task.state.toProto)
      task.completedAt.foreach(t => builder.setCompletedAt(t.getMillis))
      builder.build()
    }
  }

  implicit class ProtoToJobRunTask(val proto: Protos.JobRun.JobRunTask) extends AnyVal {
    def toModel: JobRunTask = JobRunTask(
      Task.Id(proto.getId),
      new DateTime(proto.getStartedAt, DateTimeZone.UTC),
      if (proto.hasCompletedAt) Some(new DateTime(proto.getCompletedAt, DateTimeZone.UTC)) else None,
      proto.getState.toModel
    )
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
        .setCreatedAt(jobRun.createdAt.getMillis)

      jobRun.finishedAt.foreach(date => builder.setFinishedAt(date.getMillis))
      jobRun.tasks.values.foreach(task => builder.addTasks(task.toProto))
      builder.build()
    }
  }

  implicit class ProtoToJobRun(val proto: Protos.JobRun) extends AnyVal {
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

  implicit class JobRunStatusToProto(val status: JobRunStatus) extends AnyVal {
    def toProto: Protos.JobRun.Status = Protos.JobRun.Status.valueOf(JobRunStatus.name(status))
  }

  implicit class ProtoToJobRunStatus(val proto: Protos.JobRun.Status) extends AnyVal {
    def toModel: JobRunStatus = JobRunStatus.names(proto.toString)
  }

  implicit class TaskStateToProto(val taskState: TaskState) extends AnyVal {
    def toProto: Protos.JobRun.JobRunTask.State = Protos.JobRun.JobRunTask.State.valueOf(TaskState.name(taskState))
  }

  implicit class ProtoToTaskState(val proto: Protos.JobRun.JobRunTask.State) extends AnyVal {
    def toModel: TaskState = TaskState.names(proto.toString)
  }
}
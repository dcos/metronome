package dcos.metronome
package repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model.{ JobId, JobHistory, JobRunInfo }
import dcos.metronome.repository.impl.kv.EntityMarshaller
import org.joda.time.{ DateTime, DateTimeZone }
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable

object JobHistoryMarshaller extends EntityMarshaller[JobHistory] {
  import JobHistoryConversions._

  val log = LoggerFactory.getLogger(JobHistoryMarshaller.getClass)

  override def toBytes(jobHistory: JobHistory): IndexedSeq[Byte] = {
    val builder = Protos.JobHistory.newBuilder

    builder.setJobSpecId(jobHistory.jobSpecId.toString)
    builder.setSuccessCount(jobHistory.successCount)
    builder.setFailureCount(jobHistory.failureCount)
    builder.addAllSuccessfulRuns(jobHistory.successfulRuns.toProto.asJava)
    builder.addAllFailedRuns(jobHistory.failedRuns.toProto.asJava)

    jobHistory.lastSuccessAt.foreach(lastSuccessAt => builder.setLastSuccessAt(lastSuccessAt.getMillis))
    jobHistory.lastFailureAt.foreach(lastFailureAt => builder.setLastFailureAt(lastFailureAt.getMillis))

    builder.build.toByteArray
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobHistory] =
    safeConversion { fromProto(Protos.JobHistory.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobHistory) = {
    val lastSuccessAt: Option[DateTime] =
      if (proto.hasLastSuccessAt) Some(new DateTime(proto.getLastSuccessAt, DateTimeZone.UTC)) else None

    val lastFailureAt: Option[DateTime] =
      if (proto.hasLastFailureAt) Some(new DateTime(proto.getLastFailureAt, DateTimeZone.UTC)) else None

    JobHistory(
      jobSpecId = JobId(proto.getJobSpecId),
      successCount = proto.getSuccessCount,
      failureCount = proto.getFailureCount,
      lastSuccessAt = lastSuccessAt,
      lastFailureAt = lastFailureAt,
      successfulRuns = proto.getSuccessfulRunsList.asScala.toModel,
      failedRuns = proto.getFailedRunsList.asScala.toModel)
  }
}

object JobHistoryConversions {

  implicit class JobRunInfoToProto(val jobRunInfos: Seq[JobRunInfo]) extends AnyVal {
    import JobRunConversions.JobRunIdToProto

    def toProto: Seq[Protos.JobHistory.JobRunInfo] = jobRunInfos.map { jobRunInfo =>
      Protos.JobHistory.JobRunInfo.newBuilder()
        .setJobRunId(jobRunInfo.id.toProto)
        .setCreatedAt(jobRunInfo.createdAt.getMillis)
        .setFinishedAt(jobRunInfo.finishedAt.getMillis)
        .build()
    }
  }

  implicit class ProtoToJobRunInfo(val protos: mutable.Buffer[Protos.JobHistory.JobRunInfo]) extends AnyVal {
    import JobRunConversions.ProtoToJobRunId

    def toModel: Seq[JobRunInfo] = protos.map { proto =>
      JobRunInfo(
        id = proto.getJobRunId.toModel,
        createdAt = new DateTime(proto.getCreatedAt, DateTimeZone.UTC),
        finishedAt = new DateTime(proto.getFinishedAt, DateTimeZone.UTC))
    }.toList
  }
}

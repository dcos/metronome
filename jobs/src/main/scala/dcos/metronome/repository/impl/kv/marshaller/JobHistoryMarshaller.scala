package dcos.metronome
package repository.impl.kv.marshaller

import java.time.Instant

import dcos.metronome.Protos
import dcos.metronome.model.{ JobHistory, JobId, JobRunInfo }
import dcos.metronome.repository.impl.kv.EntityMarshaller
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

    jobHistory.lastSuccessAt.foreach(lastSuccessAt => builder.setLastSuccessAt(lastSuccessAt.toEpochMilli))
    jobHistory.lastFailureAt.foreach(lastFailureAt => builder.setLastFailureAt(lastFailureAt.toEpochMilli))

    builder.build.toByteArray
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobHistory] =
    safeConversion { fromProto(Protos.JobHistory.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobHistory) = {
    val lastSuccessAt =
      if (proto.hasLastSuccessAt) Some(Instant.ofEpochMilli(proto.getLastSuccessAt)) else None

    val lastFailureAt =
      if (proto.hasLastFailureAt) Some(Instant.ofEpochMilli(proto.getLastFailureAt)) else None

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
        .setCreatedAt(jobRunInfo.createdAt.toEpochMilli)
        .setFinishedAt(jobRunInfo.finishedAt.toEpochMilli)
        .build()
    }
  }

  implicit class ProtoToJobRunInfo(val protos: mutable.Buffer[Protos.JobHistory.JobRunInfo]) extends AnyVal {
    import JobRunConversions.ProtoToJobRunId

    def toModel: Seq[JobRunInfo] = protos.map { proto =>
      JobRunInfo(
        id = proto.getJobRunId.toModel,
        createdAt = Instant.ofEpochMilli(proto.getCreatedAt),
        finishedAt = Instant.ofEpochMilli(proto.getFinishedAt))
    }.toList
  }
}

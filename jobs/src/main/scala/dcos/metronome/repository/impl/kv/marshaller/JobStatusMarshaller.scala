package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model.JobStatus
import dcos.metronome.repository.impl.kv.EntityMarshaller
import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTime, DateTimeZone }
import org.slf4j.{ Logger, LoggerFactory }

object JobStatusMarshaller extends EntityMarshaller[JobStatus] {
  val log = LoggerFactory.getLogger(JobStatusMarshaller.getClass)

  override def toBytes(jobStatus: JobStatus): IndexedSeq[Byte] = {
    val builder = Protos.JobStatus.newBuilder

    builder.setJobSpecId(jobStatus.jobSpecId.toString)
    builder.setSuccessCount(jobStatus.successCount)
    builder.setFailureCount(jobStatus.failureCount)
    jobStatus.lastSuccessAt.foreach(lastSuccessAt => builder.setLastSuccessAt(lastSuccessAt.getMillis))
    jobStatus.lastFailureAt.foreach(lastFailureAt => builder.setLastFailureAt(lastFailureAt.getMillis))

    builder.build.toByteArray
  }

  override def fromBytes(bytes: IndexedSeq[Byte]): Option[JobStatus] =
    safeConversion { fromProto(Protos.JobStatus.parseFrom(bytes.toArray)) }

  private def fromProto(proto: Protos.JobStatus) = {
    val lastSuccessAt: Option[DateTime] =
      if (proto.hasLastSuccessAt()) Some(new DateTime(proto.getLastSuccessAt, DateTimeZone.UTC)) else None

    val lastFailureAt: Option[DateTime] =
      if (proto.hasLastFailureAt()) Some(new DateTime(proto.getLastFailureAt, DateTimeZone.UTC)) else None

    JobStatus(
      jobSpecId = PathId(proto.getJobSpecId), successCount = proto.getSuccessCount,
      failureCount = proto.getFailureCount, lastSuccessAt = lastSuccessAt, lastFailureAt = lastFailureAt,
      activeRuns = Seq.empty
    )
  }
}

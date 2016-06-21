package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone }

case class JobRunId(jobId: JobId, value: String) {
  override def toString: String = s"${jobId.path.mkString(".")}.$value"
  def toPathId: PathId = jobId.toPathId / value
}

object JobRunId {
  val idFormat = DateTimeFormat.forPattern("yyyyMMddHHmmss")

  def apply(job: JobSpec): JobRunId = {
    val date = DateTime.now(DateTimeZone.UTC).toString(idFormat)
    val random = scala.util.Random.alphanumeric.take(5).mkString
    JobRunId(job.id, s"$date$random")
  }

  def apply(runSpecId: PathId): JobRunId = {
    JobRunId(JobId(runSpecId.parent), runSpecId.path.last)
  }
}

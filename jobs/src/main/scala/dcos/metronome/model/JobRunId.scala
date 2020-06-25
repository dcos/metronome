package dcos.metronome
package model

import java.time.{Clock, ZoneId}
import java.time.format.DateTimeFormatter

import mesosphere.marathon.state.PathId

case class JobRunId(jobId: JobId, value: String) {
  override def toString: String = s"${jobId.path.mkString(".")}.$value"
  def toPathId: PathId = jobId.toPathId / value
}

object JobRunId {
  val idFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyyMMddHHmmss")
    .withZone(ZoneId.systemDefault())

  def apply(job: JobSpec): JobRunId = {
    val date = idFormat.format(Clock.systemUTC().instant())
    val random = scala.util.Random.alphanumeric.take(5).mkString
    JobRunId(job.id, s"$date$random")
  }

  def apply(runSpecId: PathId): JobRunId = {
    JobRunId(JobId(runSpecId.parent), runSpecId.path.last)
  }
}

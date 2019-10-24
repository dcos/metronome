package dcos.metronome
package model

import java.time.format.DateTimeFormatter
import java.time.{ Clock, ZoneId }

import mesosphere.marathon.state.AbsolutePathId

case class JobRunId(jobId: JobId, value: String) {
  override def toString: String = s"${jobId.path.mkString(".")}.$value"
  def toPathId: AbsolutePathId = jobId.toPathId / value
}

object JobRunId {
  val idFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    .withZone(ZoneId.systemDefault())

  def apply(job: JobSpec): JobRunId = {
    val date = idFormat.format(Clock.systemUTC().instant())
    val random = scala.util.Random.alphanumeric.take(5).mkString
    JobRunId(job.id, s"$date$random")
  }

  def apply(runSpecId: AbsolutePathId): JobRunId = {
    JobRunId(JobId(runSpecId.parent), runSpecId.path.last)
  }
}

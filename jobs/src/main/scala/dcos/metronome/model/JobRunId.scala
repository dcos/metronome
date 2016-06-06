package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTimeZone, DateTime }

case class JobRunId(jobSpecId: PathId, value: String) {
  override def toString: String = s"${jobSpecId.path.mkString(".")}.$value"
}

object JobRunId {
  val idFormat = DateTimeFormat.forPattern("yyyyMMddHHmmss")

  def apply(spec: JobSpec): JobRunId = {
    val date = DateTime.now(DateTimeZone.UTC).toString(idFormat)
    val random = scala.util.Random.alphanumeric.take(5).mkString
    JobRunId(spec.id, s"$date$random")
  }
}


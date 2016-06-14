package dcos.metronome.model

import mesosphere.marathon.state.PathId
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone }

case class JobRunId(jobSpecId: PathId, value: String) {
  override def toString: String = s"${jobSpecId.path.mkString(".")}.$value"
}

object JobRunId {
  val idFormat = DateTimeFormat.forPattern("yyyyMMddHHmmss")
  private[this] val RunSpecRegex = """^(.*)\.(.*)$""".r

  def apply(spec: JobSpec): JobRunId = {
    val date = DateTime.now(DateTimeZone.UTC).toString(idFormat)
    val random = scala.util.Random.alphanumeric.take(5).mkString
    JobRunId(spec.id, s"$date$random")
  }

  // TODO: parsing runSpecId to extract a JobRunId is ugly. It would be better to let the
  // launch queue create taskIds based on a convention that we define and can safely deserialize.
  def apply(runSpecId: PathId): JobRunId = runSpecId.toString() match {
    case RunSpecRegex(runSpecString, jobRunValue) =>
      // Marathon doesn't recognise our dot delimiter: the appId will not correctly be split up
      val runSpecId = PathId(runSpecString.replace(".", "/"))
      JobRunId(runSpecId, jobRunValue)

    case _ =>
      throw new MatchError(s"runSpecId $runSpecId is no valid identifier")
  }
}

package dcos.metronome.model

import com.wix.accord._
import com.wix.accord.dsl._
import mesosphere.marathon.plugin
import mesosphere.marathon.state.PathId

case class JobId(path: List[String]) extends plugin.PathId {

  def safePath: String = path.mkString("_")

  def toPathId: PathId = PathId(path)

  override lazy val toString: String = path.mkString(".")
}

object JobId {

  def apply(in: String): JobId = {
    JobId(in.split("[.]").filter(_.nonEmpty).toList)
  }

  def apply(pathId: PathId): JobId = {
    JobId(pathId.path)
  }

  def fromSafePath(in: String): JobId = {
    JobId(in.split("_").filter(_.nonEmpty).toList)
  }

  implicit lazy val validJobId: Validator[JobId] = validator[JobId] { id =>
    id is validPathChars
  }

  private[this] val ID_PATH_SEGMENT_PATTERN =
    "^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$".r

  private val validPathChars = new Validator[JobId] {
    override def apply(pathId: JobId): Result = {
      validate(pathId.path)(validator = pathId.path.each should matchRegexFully(ID_PATH_SEGMENT_PATTERN.pattern))
    }
  }
}

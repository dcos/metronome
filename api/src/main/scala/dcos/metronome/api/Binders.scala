package dcos.metronome
package api

import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.model.JobId
import play.api.mvc.{ QueryStringBindable, PathBindable }
import mesosphere.marathon.api.v2.Validation.validateOrThrow

import scala.util.control.NonFatal

object Binders {

  //Define types here to use them in routes file without full classified name
  type JobId = dcos.metronome.model.JobId
  type Embed = dcos.metronome.jobinfo.JobInfo.Embed

  /**
    * This binder binds path parameter to JobIds
    */
  implicit val pathPathBinder: PathBindable[JobId] = new PathBindable[JobId] {
    override def bind(key: String, value: String): Either[String, JobId] = {
      try {
        Right(validateOrThrow(JobId(value)))
      } catch {
        case NonFatal(ex) => Left(s"Not a valid id: $value")
      }
    }
    override def unbind(key: String, value: JobId): String = value.toString
  }

  /**
    * This binder binds query parameter to JobIds
    */
  implicit val pathQueryBinder: QueryStringBindable[JobId] = new QueryStringBindable[JobId] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, JobId]] = {
      val jobId = params.get(key).flatMap(_.headOption)
      try {
        jobId.map(value => Right(validateOrThrow(JobId(value))))
      } catch {
        case NonFatal(ex) => Some(Left(s"Not a valid id: $jobId"))
      }
    }
    override def unbind(key: String, value: JobId): String = value.toString
  }

  implicit val embedQueryBinder: QueryStringBindable[Set[Embed]] = new QueryStringBindable[Set[Embed]] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Set[Embed]]] = {
      val embeds = params.getOrElse(key, Seq.empty).flatMap(_.split(","))
      val valid = embeds.flatMap(Embed.names.get)
      if (valid.size != embeds.size) Some(Left(s"Unknown embed options. Valid options are: ${Embed.names.keys.mkString(", ")}"))
      else Some(Right(valid.toSet))
    }
    override def unbind(key: String, value: Set[Embed]): String = value.map(_.toString).mkString(",")
  }
}

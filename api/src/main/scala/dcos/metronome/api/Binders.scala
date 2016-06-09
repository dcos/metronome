package dcos.metronome.api

import dcos.metronome.jobinfo.JobInfo.Embed
import mesosphere.marathon.state.PathId
import play.api.mvc.{ QueryStringBindable, PathBindable }
import mesosphere.marathon.api.v2.Validation.validateOrThrow

import scala.util.control.NonFatal

object Binders {

  //Define types here to use them in routes file without full classified name
  type PathId = mesosphere.marathon.state.PathId
  type Embed = dcos.metronome.jobinfo.JobInfo.Embed

  /**
    * This binder binds path parameter to PathIds
    */
  implicit val pathPathBinder: PathBindable[PathId] = new PathBindable[PathId] {
    override def bind(key: String, value: String): Either[String, PathId] = {
      try {
        //TODO: make this functionality available in Marathon
        Right(validateOrThrow(PathId(value.split("[.]").toList, absolute = true)))
      } catch {
        case NonFatal(ex) => Left(s"Not a valid id: $value")
      }
    }
    override def unbind(key: String, value: PathId): String = value.path.mkString(".")
  }

  /**
    * This binder binds query parameter to PathIds
    */
  implicit val pathQueryBinder: QueryStringBindable[PathId] = new QueryStringBindable[PathId] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PathId]] = {
      val pathId = params.get(key).flatMap(_.headOption)
      try {
        //TODO: make this functionality available in Marathon
        pathId.map(value => Right(validateOrThrow(PathId(value.split("[.]").toList, absolute = true))))
      } catch {
        case NonFatal(ex) => Some(Left(s"Not a valid id: $pathId"))
      }
    }
    override def unbind(key: String, value: PathId): String = value.path.mkString(".")
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

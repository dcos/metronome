package dcos.metronome
package api.v0.controllers

import akka.stream.Materializer
import dcos.metronome.{ JobSpecAlreadyExists, JobSpecDoesNotExist }
import dcos.metronome.api._
import dcos.metronome.api.v1.models.{ JobSpecFormat => _, _ }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobRunSpec, JobSpec, ScheduleSpec }
import mesosphere.marathon.api.v2.json.Formats.FormatWithDefault
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer, CreateRunSpec, UpdateRunSpec }
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.{ AnyContent, BodyParser }

import scala.concurrent.ExecutionContext

class ScheduledJobSpecController(
  jobSpecService:        JobSpecService,
  val authenticator:     Authenticator,
  val authorizer:        Authorizer,
  val config:            ApiConfig,
  val mat:               Materializer,
  val defaultBodyParser: BodyParser[AnyContent])(implicit ec: ExecutionContext) extends AuthorizedController {

  import ScheduledJobSpecController._

  def createJob = AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
    request.authorizedAsync(CreateRunSpec) { jobSpec =>
      jobSpecService.createJobSpec(jobSpec)
        .map(Created(_))
        .recover {
          case JobSpecAlreadyExists(_) => Conflict(ErrorDetail("Job with this id already exists"))
        }
    }
  }

  def updateJob(id: JobId) = AuthorizedAction.async(validate.jsonWith[JobSpec](_.copy(id = id))) { implicit request =>
    request.authorizedAsync(UpdateRunSpec) { jobSpec =>
      jobSpecService.updateJobSpec(id, _ => jobSpec).map(Ok(_)).recover {
        case _: JobSpecDoesNotExist => NotFound(UnknownJob(id))
      }
    }
  }
}

object ScheduledJobSpecController {
  implicit lazy val JobSpecWithScheduleFormat: Format[JobSpec] = (
    (__ \ "id").format[JobId] ~
    (__ \ "description").formatNullable[String] ~
    (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
    (__ \ "schedules").formatNullable[Seq[ScheduleSpec]].withDefault(Seq.empty) ~
    (__ \ "run").format[JobRunSpec])(JobSpec.apply, unlift(JobSpec.unapply))

  implicit lazy val JobSpecSchema: JsonSchema[JobSpec] = JsonSchema.fromResource("/public/api/v0/schema/jobspec_v0.schema.json")
}

package dcos.metronome.api.v1.controllers

import dcos.metronome.JobSpecDoesNotExist
import dcos.metronome.api.{ UnknownJob, Authorization }
import dcos.metronome.api.v1.models.{ JobSpecFormat => _, _ }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobSpec, JobRunSpec, ScheduleSpec }
import mesosphere.marathon.api.v2.json.Formats.FormatWithDefault
import mesosphere.marathon.plugin.auth.{ UpdateRunSpec, CreateRunSpec, Authenticator, Authorizer }
import mesosphere.marathon.state.PathId
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.immutable._

class ScheduledJobSpecController(
    jobSpecService:    JobSpecService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import ScheduledJobSpecController.JobSpecWithScheduleFormat

  def createJob = AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
    request.authorizedAsync(CreateRunSpec) { jobSpec =>
      jobSpecService.createJobSpec(jobSpec).map(Created(_))
    }
  }

  def updateJob(id: PathId) = AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
    request.authorizedAsync(UpdateRunSpec) { jobSpec =>
      jobSpecService.updateJobSpec(id, _ => jobSpec).map(Ok(_)).recover {
        case ex: JobSpecDoesNotExist => NotFound(UnknownJob(id))
      }
    }
  }
}

object ScheduledJobSpecController {
  implicit lazy val JobSpecWithScheduleFormat: Format[JobSpec] = (
    (__ \ "id").format[PathId] ~
    (__ \ "description").formatNullable[String] ~
    (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
    (__ \ "schedules").formatNullable[Seq[ScheduleSpec]].withDefault(Seq.empty) ~
    (__ \ "run").format[JobRunSpec]
  )(JobSpec.apply, unlift(JobSpec.unapply))
}
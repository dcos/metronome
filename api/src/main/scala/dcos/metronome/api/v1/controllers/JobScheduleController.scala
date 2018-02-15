package dcos.metronome
package api.v1.controllers

import dcos.metronome.JobSpecDoesNotExist
import dcos.metronome.api._
import dcos.metronome.api.v1.models._
import dcos.metronome.api.v1.models.schema._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, ScheduleSpec, JobSpec }
import mesosphere.marathon.plugin.auth._
import JobId._
import play.api.mvc.Result

import scala.async.Async.{ async, await }
import scala.concurrent.Future

class JobScheduleController(
  jobSpecService:    JobSpecService,
  val authenticator: Authenticator,
  val authorizer:    Authorizer,
  val config:        ApiConfig) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getSchedules(id: JobId) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id).map {
      case Some(job) => request.authorized(ViewRunSpec, job, Ok(job.schedules))
      case None      => NotFound(UnknownJob(id))
    }
  }

  def getSchedule(id: JobId, scheduleId: String) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id).map {
      case Some(job) if job.schedule(scheduleId).isDefined => request.authorized(ViewRunSpec, job, Ok(job.schedule(scheduleId)))
      case Some(job) => NotFound(UnknownSchedule(scheduleId))
      case None => NotFound(UnknownJob(id))
    }
  }

  def createSchedule(id: JobId) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
    def addSchedule(jobSpec: JobSpec) = {
      val scheduleId = request.body.id
      require(jobSpec.schedules.count(_.id == scheduleId) == 0, s"A schedule with id $scheduleId already exists")
      require(jobSpec.schedules.isEmpty, "Only one schedule supported at the moment")
      jobSpec.copy(schedules = request.body +: jobSpec.schedules)
    }
    withJobSpec(id) { spec =>
      jobSpecService.updateJobSpec(id, addSchedule)
        .map(job => Created(job.schedule(request.body.id)))
        .recover {
          case JobSpecDoesNotExist(_)       => NotFound(UnknownJob(id))
          case ex: IllegalArgumentException => Conflict(ErrorDetail(ex.getMessage))
        }
    }
  }

  def updateSchedule(id: JobId, scheduleId: String) = AuthorizedAction.async(validate.jsonWith[ScheduleSpec](_.copy(id = scheduleId))) { implicit request =>
    def changeSchedule(jobSpec: JobSpec) = {
      require(jobSpec.schedules.count(_.id == scheduleId) == 1, "Can only update an existing schedule")
      jobSpec.copy(schedules = request.body +: jobSpec.schedules.filterNot(_.id == scheduleId))
    }
    withJobSpec(id) { spec =>
      jobSpecService.updateJobSpec(id, changeSchedule)
        .map(job => Ok(job.schedule(scheduleId)))
        .recover {
          case JobSpecDoesNotExist(_)       => NotFound(UnknownJob(id))
          case ex: IllegalArgumentException => NotFound(UnknownSchedule(scheduleId))
        }
    }
  }

  def deleteSchedule(id: JobId, scheduleId: String) = AuthorizedAction.async { implicit request =>
    def deleteSchedule(jobSpec: JobSpec) = {
      require(jobSpec.schedules.count(_.id == scheduleId) == 1, "Can only delete an existing schedule")
      jobSpec.copy(schedules = jobSpec.schedules.filterNot(_.id == scheduleId))
    }

    withJobSpec(id) { spec =>
      if (spec.schedules.count(_.id == scheduleId) != 1) {
        Future.successful(NotFound(UnknownSchedule(scheduleId)))
      } else {
        jobSpecService.updateJobSpec(id, deleteSchedule)
          .map(_ => Ok)
          .recover {
            case JobSpecDoesNotExist(_) => NotFound(UnknownJob(id))
          }
      }
    }
  }

  private def withJobSpec[R](id: JobId)(fn: JobSpec => Future[Result])(implicit request: AuthorizedRequest[R]): Future[Result] = {
    async {
      await(jobSpecService.getJobSpec(id)) match {
        case Some(jobSpec) =>
          await(request.authorizedAsync(UpdateRunSpec, jobSpec) { fn })
        case None =>
          NotFound(UnknownJob(id))
      }
    }
  }
}

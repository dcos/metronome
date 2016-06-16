package dcos.metronome.api.v1.controllers

import dcos.metronome.JobSpecDoesNotExist
import dcos.metronome.api._
import dcos.metronome.api.v1.models._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ ScheduleSpec, JobSpec }
import mesosphere.marathon.plugin.RunSpec
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.state.PathId
import PathId._
import play.api.mvc.Result

import scala.concurrent.Future

class JobScheduleController(
    jobSpecService:    JobSpecService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getSchedules(id: PathId) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id).map {
      case Some(job) => request.authorized(ViewRunSpec, job, Ok(job.schedules))
      case None      => NotFound(UnknownJob(id))
    }
  }

  def getSchedule(id: PathId, scheduleId: String) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id).map {
      case Some(job) if job.schedule(scheduleId).isDefined => request.authorized(ViewRunSpec, job, Ok(job.schedule(scheduleId)))
      case Some(job) => NotFound(UnknownSchedule(scheduleId))
      case None => NotFound(UnknownJob(id))
    }
  }

  def createSchedule(id: PathId) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
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

  def updateSchedule(id: PathId, scheduleId: String) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
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

  def deleteSchedule(id: PathId, scheduleId: String) = AuthorizedAction.async { implicit request =>
    def deleteSchedule(jobSpec: JobSpec) = {
      require(jobSpec.schedules.count(_.id == scheduleId) == 1, "Can only delete an existing schedule")
      jobSpec.copy(schedules = jobSpec.schedules.filterNot(_.id == scheduleId))
    }

    withJobSpec(id) { spec =>
      jobSpecService.updateJobSpec(id, deleteSchedule)
        .map(_ => Ok)
        .recover{
          case JobSpecDoesNotExist(_)       => NotFound(UnknownJob(id))
          case ex: IllegalArgumentException => NotFound(UnknownSchedule(scheduleId))
        }
    }
  }

  private def withJobSpec[R](id: PathId)(fn: JobSpec => Future[Result])(implicit request: AuthorizedRequest[R]): Future[Result] = {
    jobSpecService.getJobSpec(id).flatMap {
      case Some(jobSpec) => request.authorizedAsync(UpdateRunSpec, jobSpec) { fn }
      case None          => Future.successful(NotFound(UnknownJob(id)))
    }
  }
}


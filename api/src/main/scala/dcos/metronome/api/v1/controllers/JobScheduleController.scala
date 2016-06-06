package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ UnknownSchedule, UnknownJob, Authorization }
import dcos.metronome.api.v1.models._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ ScheduleSpec, JobSpec }
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import mesosphere.marathon.state.PathId
import PathId._

class JobScheduleController(
    jobSpecService:    JobSpecService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getSchedules(id: PathId) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
    jobSpecService.getJobSpec(id).map {
      case Some(job) => Ok(job.schedules)
      case None      => NotFound(UnknownJob(id))
    }
  }

  def getSchedule(id: PathId, scheduleId: String) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
    jobSpecService.getJobSpec(id).map {
      case Some(job) if job.schedule(scheduleId).isDefined => Ok(job.schedule(scheduleId))
      case Some(job) => NotFound(UnknownSchedule(scheduleId))
      case None => NotFound(UnknownJob(id))
    }
  }

  def createSchedule(id: PathId) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
    def addSchedule(jobSpec: JobSpec) = {
      val scheduleId = request.body.id
      require(jobSpec.schedules.count(_.id == scheduleId) == 0, "Can only create a non existing schedule")
      jobSpec.copy(schedules = request.body +: jobSpec.schedules)
    }
    jobSpecService.updateJobSpec(id, addSchedule).map(Ok(_))
  }

  def updateSchedule(id: PathId, scheduleId: String) = AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
    def changeSchedule(jobSpec: JobSpec) = {
      require(jobSpec.schedules.count(_.id == scheduleId) == 1, "Can only update an existing schedule")
      jobSpec.copy(schedules = request.body +: jobSpec.schedules.filterNot(_.id == scheduleId))
    }
    jobSpecService.updateJobSpec(id, changeSchedule).map(Ok(_))
  }

  def deleteSchedule(id: PathId, scheduleId: String) = AuthorizedAction.async { implicit request =>
    def deleteSchedule(jobSpec: JobSpec) = {
      require(jobSpec.schedules.count(_.id == scheduleId) == 1, "Can only delete an existing schedule")
      jobSpec.copy(schedules = jobSpec.schedules.filterNot(_.id == scheduleId))
    }
    jobSpecService.updateJobSpec(id, deleteSchedule).map(Ok(_))
  }
}


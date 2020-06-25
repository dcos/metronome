package dcos.metronome
package api.v1.controllers

import dcos.metronome.api._
import dcos.metronome.api.v1.models._
import dcos.metronome.api.v1.models.schema._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{JobId, JobSpec, ScheduleSpec}
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.metrics.Metrics
import play.api.mvc.{ControllerComponents, Result}

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class JobScheduleController(
    cc: ControllerComponents,
    jobSpecService: JobSpecService,
    metrics: Metrics,
    authenticator: Authenticator,
    authorizer: Authorizer,
    config: ApiConfig
)(implicit ec: ExecutionContext)
    extends Authorization(cc, metrics, authenticator, authorizer, config) {

  def getSchedules(id: JobId) =
    measured {
      AuthorizedAction.async { implicit request =>
        jobSpecService.getJobSpec(id).map {
          case Some(job) => request.authorized(ViewRunSpec, job, Ok(job.schedules))
          case None => NotFound(UnknownJob(id))
        }
      }
    }

  def getSchedule(id: JobId, scheduleId: String) =
    measured {
      AuthorizedAction.async { implicit request =>
        jobSpecService.getJobSpec(id).map {
          case Some(job) if job.schedule(scheduleId).isDefined =>
            request.authorized(ViewRunSpec, job, Ok(job.schedule(scheduleId)))
          case Some(_) => NotFound(UnknownSchedule(scheduleId))
          case None => NotFound(UnknownJob(id))
        }
      }
    }

  def createSchedule(id: JobId) =
    measured {
      AuthorizedAction.async(validate.json[ScheduleSpec]) { implicit request =>
        def addSchedule(jobSpec: JobSpec) = {
          val scheduleId = request.body.id
          require(jobSpec.schedules.count(_.id == scheduleId) == 0, s"A schedule with id $scheduleId already exists")
          require(jobSpec.schedules.isEmpty, "Only one schedule supported at the moment")
          jobSpec.copy(schedules = request.body +: jobSpec.schedules)
        }
        withAuthorization(id) { _ =>
          jobSpecService
            .updateJobSpec(id, addSchedule)
            .map(job => Created(job.schedule(request.body.id)))
            .recover {
              case JobSpecDoesNotExist(_) => NotFound(UnknownJob(id))
              case ex: IllegalArgumentException => Conflict(ErrorDetail(ex.getMessage))
            }
        }
      }
    }

  def updateSchedule(id: JobId, scheduleId: String) =
    measured {
      AuthorizedAction.async(validate.jsonWith[ScheduleSpec](_.copy(id = scheduleId))) { implicit request =>
        def changeSchedule(jobSpec: JobSpec) = {
          require(jobSpec.schedules.count(_.id == scheduleId) == 1, "Can only update an existing schedule")
          jobSpec.copy(schedules = request.body +: jobSpec.schedules.filterNot(_.id == scheduleId))
        }
        withAuthorization(id) { _ =>
          jobSpecService
            .updateJobSpec(id, changeSchedule)
            .map(job => Ok(job.schedule(scheduleId)))
            .recover {
              case JobSpecDoesNotExist(_) => NotFound(UnknownJob(id))
              case _: IllegalArgumentException => NotFound(UnknownSchedule(scheduleId))
            }
        }
      }
    }

  def deleteSchedule(id: JobId, scheduleId: String) =
    measured {
      AuthorizedAction.async { implicit request =>
        def deleteSchedule(jobSpec: JobSpec) = {
          if (jobSpec.schedules.count(_.id == scheduleId) != 1) {
            throw JobSpecWithNoSchedule(jobSpec.id)
          }
          jobSpec.copy(schedules = jobSpec.schedules.filterNot(_.id == scheduleId))
        }

        withAuthorization(id) { _ =>
          jobSpecService
            .updateJobSpec(id, deleteSchedule)
            .map(_ => Ok)
            .recover {
              case JobSpecDoesNotExist(_) => NotFound(UnknownJob(id))
              case JobSpecWithNoSchedule(_) => NotFound(UnknownSchedule(scheduleId))
            }
        }
      }
    }

  private def withAuthorization[R](
      id: JobId
  )(fn: JobSpec => Future[Result])(implicit request: AuthorizedRequest[R]): Future[Result] = {
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

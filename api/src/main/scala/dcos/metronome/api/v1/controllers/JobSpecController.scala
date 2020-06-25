package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.v1.models.schema._
import dcos.metronome.api.{ApiConfig, Authorization, ErrorDetail, UnknownJob}
import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.JobInfoService
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{JobId, JobSpec}
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.metrics.Metrics
import play.api.mvc.{ControllerComponents, Result}

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class JobSpecController(
    cc: ControllerComponents,
    jobSpecService: JobSpecService,
    jobRunService: JobRunService,
    jobInfoService: JobInfoService,
    authenticator: Authenticator,
    authorizer: Authorizer,
    config: ApiConfig,
    metrics: Metrics
)(implicit ec: ExecutionContext)
    extends Authorization(cc, metrics, authenticator, authorizer, config) {

  def createJob =
    measured {
      AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
        request.authorizedAsync(CreateRunSpec) { jobSpec =>
          jobSpecService
            .createJobSpec(jobSpec)
            .map(Created(_))
            .recover {
              case JobSpecAlreadyExists(_) => Conflict(ErrorDetail("Job with this id already exists"))
            }
        }
      }
    }

  def listJobs(embed: Set[Embed]) =
    measured {
      AuthorizedAction.async { implicit request =>
        jobInfoService.selectJobs(request.selectAuthorized, embed).map(Ok(_))
      }
    }

  def getJob(id: JobId, embed: Set[Embed]) =
    measured {
      AuthorizedAction.async { implicit request =>
        jobInfoService.selectJob(id, request.selectAuthorized, embed).map {
          case Some(job) => Ok(job)
          case None => NotFound(UnknownJob(id))
        }
      }
    }

  def updateJob(id: JobId) =
    measured {
      AuthorizedAction.async(validate.jsonWith[JobSpec](_.copy(id = id))) { implicit request =>
        request.authorizedAsync(UpdateRunSpec) { jobSpec =>
          def updateJob(job: JobSpec): JobSpec = jobSpec.copy(schedules = job.schedules)
          jobSpecService.updateJobSpec(id, updateJob).map(Ok(_)).recover {
            case _: JobSpecDoesNotExist => NotFound(UnknownJob(id))
          }
        }
      }
    }

  def deleteJob(id: JobId, stopCurrentJobRuns: Boolean) =
    measured {
      AuthorizedAction.async { implicit request =>
        def deleteJobSpec(jobSpec: JobSpec): Future[Result] = {
          jobRunService.activeRuns(id).flatMap {
            case _ :: _ if !stopCurrentJobRuns =>
              Future
                .successful(Conflict(ErrorDetail("There are active job runs. Override with stopCurrentJobRuns=true")))
            case runs =>
              val f = for {
                _ <- Future.sequence(runs.map(run => jobRunService.killJobRun(run.jobRun.id)))
                deletedJobSpec <- jobSpecService.deleteJobSpec(id)
              } yield Ok(deletedJobSpec)

              f.recover {
                case JobSpecDoesNotExist(_) => NotFound(UnknownJob(id))
              }
          }
        }

        async {
          await(jobSpecService.getJobSpec(id)) match {
            case Some(jobSpec) =>
              await(request.authorizedAsync(DeleteRunSpec, jobSpec) { deleteJobSpec })
            case None => NotFound(UnknownJob(id))
          }
        }
      }
    }
}

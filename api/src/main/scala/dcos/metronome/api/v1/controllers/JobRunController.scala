package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ ApiConfig, Authorization, ErrorDetail, UnknownJob, UnknownJobRun }
import dcos.metronome.jobrun.{ JobRunService, StartedJobRun }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobRunId }
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer, UpdateRunSpec, ViewRunSpec }
import play.api.mvc.ControllerComponents

import scala.async.Async.{ async, await }
import scala.concurrent.ExecutionContext

class JobRunController(
  cc:             ControllerComponents,
  jobSpecService: JobSpecService,
  jobRunService:  JobRunService,
  metrics:        Metrics,
  authenticator:  Authenticator,
  authorizer:     Authorizer,
  config:         ApiConfig)(implicit ec: ExecutionContext) extends Authorization(cc, metrics, authenticator, authorizer, config) {

  def getAllJobRuns = measured {
    AuthorizedAction.async { implicit request =>
      jobRunService.listRuns(request.isAllowed).map(Ok(_))
    }
  }

  def getJobRuns(id: JobId) = measured {
    AuthorizedAction.async { implicit request =>
      async {
        await(jobSpecService.getJobSpec(id)) match {
          case Some(spec) =>
            if (request.isAllowed(spec)) {
              await {
                jobRunService.activeRuns(id).map(Ok(_))
              }
            } else {
              Ok(Array.empty[StartedJobRun])
            }
          case None => NotFound(UnknownJob(id))
        }
      }
    }
  }

  def getJobRun(id: JobId, runId: String) = measured {
    AuthorizedAction.async { implicit request =>
      async {
        await(jobRunService.getJobRun(JobRunId(id, runId))) match {
          case Some(run) => request.authorized(ViewRunSpec, run.jobRun.jobSpec, Ok(run))
          case None      => NotFound(UnknownJobRun(id, runId))
        }
      }
    }
  }

  def killJobRun(id: JobId, runId: String) = measured {
    AuthorizedAction.async { implicit request =>

      async {
        await(jobSpecService.getJobSpec(id)) match {
          case Some(spec) =>
            await {
              request.authorizedAsync(UpdateRunSpec, spec) { _ =>
                jobRunService.killJobRun(JobRunId(id, runId)).map(Ok(_)).recover {
                  case JobRunDoesNotExist(_) => NotFound(UnknownJobRun(id, runId))
                }
              }
            }
          case None => NotFound(UnknownJob(id))
        }
      }
    }
  }

  def triggerJob(id: JobId) = measured {
    AuthorizedAction.async { implicit request =>
      async {
        await(jobSpecService.getJobSpec(id)) match {
          case Some(spec) =>
            await {
              request.authorizedAsync(UpdateRunSpec, spec) { _ =>
                // the schedule is considered the source of truth wrt scheduling behavior, so we're passing it along
                // here. This way we can check via schedule.concurrencyPolicy whether or not another JobRun can be
                // triggered. Automated/manual will be treated equally for this evaluation.
                jobRunService.startJobRun(spec, spec.schedules.headOption).map(Created(_)).recover {
                  case ex: ConcurrentJobRunNotAllowed => Conflict(ErrorDetail(ex.getMessage))
                }
              }
            }
          case None =>
            NotFound(UnknownJob(id))
        }
      }
    }
  }
}

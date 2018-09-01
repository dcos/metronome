package dcos.metronome
package api.v1.controllers

import akka.stream.Materializer
import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ ApiConfig, Authorization, UnknownJob, UnknownJobRun }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobRunId }
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer, UpdateRunSpec, ViewRunSpec }
import play.api.mvc.ControllerComponents

import scala.async.Async.{ async, await }
import scala.concurrent.ExecutionContext

class JobRunController(cc: ControllerComponents)(
  implicit
  ec: ExecutionContext, jobSpecService: JobSpecService,
  jobRunService: JobRunService, metrics: Metrics,
  authenticator: Authenticator,
  authorizer:    Authorizer,
  config:        ApiConfig,
  mat:           Materializer) extends Authorization(cc) {

  def getAllJobRuns = measured {
    AuthorizedAction.async { implicit request =>
      jobRunService.listRuns(request.isAllowed).map(Ok(_))
    }
  }

  def getJobRuns(id: JobId) = measured {
    AuthorizedAction.async { implicit request =>
      jobRunService.activeRuns(id).map(_.filter(request.isAllowed)).map(Ok(_))
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
                jobRunService.startJobRun(spec).map(Created(_))
              }
            }
          case None =>
            NotFound(UnknownJob(id))
        }
      }
    }
  }
}

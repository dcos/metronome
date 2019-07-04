package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.v1.models.schema._
import dcos.metronome.api.{ ApiConfig, Authorization, UnknownJob, UnknownJobRun }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobRunId, JobRunSpecOverrides }
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer, UpdateRunSpec, ViewRunSpec }
import org.slf4j.LoggerFactory
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
    AuthorizedAction.async(validate.optionalJson[JobRunSpecOverrides]) { implicit request =>
      val overrides = request.body
      JobRunController.log.info(s"overrides: $overrides")

      async {
        await(jobSpecService.getJobSpec(id)) match {
          case Some(spec) =>
            await {
              request.authorizedAsync(UpdateRunSpec, spec) { _ =>
                jobRunService.startJobRun(jobSpec = spec, schedule = None, overrides = overrides).map(Created(_))
              }
            }
          case None =>
            NotFound(UnknownJob(id))
        }
      }
    }
  }
}

object JobRunController {
  val log = LoggerFactory.getLogger(getClass)
}
package dcos.metronome
package api.v1.controllers

import dcos.metronome.JobRunDoesNotExist
import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ ApiConfig, Authorization, UnknownJob, UnknownJobRun }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobRunId }
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer, UpdateRunSpec, ViewRunSpec }

import scala.async.Async.{ async, await }

class JobRunController(
  jobSpecService:    JobSpecService,
  jobRunService:     JobRunService,
  val authenticator: Authenticator,
  val authorizer:    Authorizer,
  val config:        ApiConfig) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getAllJobRuns = AuthorizedAction.async { implicit request =>
    jobRunService.listRuns(request.isAllowed).map(Ok(_))
  }

  def getJobRuns(id: JobId) = AuthorizedAction.async { implicit request =>
    jobRunService.activeRuns(id).map(_.filter(request.isAllowed)).map(Ok(_))
  }

  def getJobRun(id: JobId, runId: String) = AuthorizedAction.async { implicit request =>
    async {
      await(jobRunService.getJobRun(JobRunId(id, runId))) match {
        case Some(run) => request.authorized(ViewRunSpec, run.jobRun.jobSpec, Ok(run))
        case None      => NotFound(UnknownJobRun(id, runId))
      }
    }
  }

  def killJobRun(id: JobId, runId: String) = AuthorizedAction.async { implicit request =>

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

  def triggerJob(id: JobId) = AuthorizedAction.async { implicit request =>
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

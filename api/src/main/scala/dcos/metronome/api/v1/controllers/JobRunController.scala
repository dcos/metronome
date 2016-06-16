package dcos.metronome.api.v1.controllers

import dcos.metronome.JobRunDoesNotExist
import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ Authorization, UnknownJob, UnknownJobRun }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.JobRunId
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer, UpdateRunSpec, ViewRunSpec }
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

class JobRunController(
    jobSpecService:    JobSpecService,
    jobRunService:     JobRunService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getAllJobRuns = AuthorizedAction.async { implicit request =>
    jobRunService.listRuns(request.isAllowed).map(Ok(_))
  }

  def getJobRuns(id: PathId) = AuthorizedAction.async { implicit request =>
    jobRunService.activeRuns(id).map(_.filter(request.isAllowed)).map(Ok(_))
  }

  def getJobRun(id: PathId, runId: String) = AuthorizedAction.async { implicit request =>
    jobRunService.getJobRun(JobRunId(id, runId)).map {
      case Some(run) => request.authorized(ViewRunSpec, run, Ok(run))
      case None      => NotFound(UnknownJobRun(id, runId))
    }
  }

  def killJobRun(id: PathId, runId: String) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id).flatMap {
      case Some(spec) => request.authorizedAsync(UpdateRunSpec, spec) { _ =>
        jobRunService.killJobRun(JobRunId(id, runId)).map(Ok(_)).recover {
          case JobRunDoesNotExist(_) => NotFound(UnknownJobRun(id, runId))
        }
      }
      case None => Future.successful(NotFound(UnknownJob(id)))
    }
  }

  def triggerJob(id: PathId) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id).flatMap {
      case Some(spec) => request.authorizedAsync(UpdateRunSpec, spec) { _ =>
        jobRunService.startJobRun(spec).map(Created(_))
      }
      case None => Future.successful(NotFound(UnknownJob(id)))
    }
  }
}

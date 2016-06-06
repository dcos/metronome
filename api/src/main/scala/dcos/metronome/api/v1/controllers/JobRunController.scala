package dcos.metronome.api.v1.controllers

import dcos.metronome.api.Authorization
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.{ JobSpec, JobRunId }
import mesosphere.marathon.plugin.auth.{ Authorizer, Authenticator }
import dcos.metronome.api.v1.models._

import scala.concurrent.Future

class JobRunController(
    jobRunService:     JobRunService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def getAllJobRuns = AuthorizedAction.async { implicit request =>
    jobRunService.listRuns(_ => true).map(Ok(_))
  }

  def getJobRun(id: String) = AuthorizedAction.async { implicit request =>
    jobRunService.getJobRun(JobRunId(id)).map {
      case Some(run) => Ok(run)
      case None      => NotFound
    }
  }

  def changeJobRun(id: String, action: String) = AuthorizedAction.async { implicit request =>
    action match {
      case "stop" => jobRunService.killJobRun(JobRunId(id)).map(Ok(_))
      case _      => Future.successful(BadRequest(s"Action unknown: $action"))
    }
  }

  def runJob(wait: Boolean) = AuthorizedAction.async(parse.json[JobSpec]) { implicit request =>
    // TODO: question: should we parse only a runspec here?
    // Problem: do we need the PathId for authorization?
    // Problem: should we make JobSpec.labels part of RunSpec?
    jobRunService.startJobRun(request.body).flatMap { result =>
      if (wait) result.complete.map(Ok(_)) else Future.successful(Ok(result))
    }
  }
}

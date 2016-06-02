package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ UnknownJob, Authorization }
import dcos.metronome.api.v1.models._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.JobSpec
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import mesosphere.marathon.state.PathId
import PathId._

class JobSpecController(
    jobSpecService:    JobSpecService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def createJob = AuthorizedAction.async(parse.json[JobSpec]) { implicit request =>
    jobSpecService.createJobSpec(request.body).map(Created(_))
  }

  def listJobs = AuthorizedAction.async { implicit request =>
    jobSpecService.listJobSpecs(_ => true).map(Ok(_))
  }

  def getJob(id: String) = AuthorizedAction.async { implicit request =>
    jobSpecService.getJobSpec(id.toRootPath).map {
      case Some(job) => Ok(job)
      case None      => NotFound(UnknownJob(id))
    }
  }

  def updateJob(id: String) = AuthorizedAction.async(parse.json[JobSpec]) { implicit request =>
    jobSpecService.updateJobSpec(id.toRootPath, _ => request.body).map(Ok(_))
  }

  def deleteJob(id: String) = AuthorizedAction.async { implicit request =>
    jobSpecService.deleteJobSpec(id.toRootPath).map(Ok(_))
  }

  def triggerJob(id: String) = AuthorizedAction { implicit request =>
    Ok
  }
}

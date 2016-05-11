package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ ErrorDetail, Authorization }
import dcos.metronome.api.v1.models._
import dcos.metronome.model.JobSpec
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import mesosphere.marathon.state.PathId
import PathId._

class JobsController(val authenticator: Authenticator, val authorizer: Authorizer) extends Authorization {

  var jobs = Map.empty[PathId, JobSpec]

  def createJob = AuthorizedAction(parse.json[JobSpec]) { implicit request =>
    val job = request.body
    jobs += job.id -> job
    Created(request.body)
  }

  def listJobs = AuthorizedAction { implicit request =>
    Ok(jobs.values)
  }

  def getJob(id: String) = AuthorizedAction { implicit request =>
    jobs.get(id.toRootPath) match {
      case Some(job) => Ok(job)
      case None      => NotFound(ErrorDetail(s"No job with this id: $id"))
    }
  }

  def updateJob(id: String) = AuthorizedAction { implicit request =>
    jobs.get(id.toRootPath) match {
      case Some(job) =>
        jobs += job.id -> job
        Ok(job)
      case None => NotFound(ErrorDetail(s"No job with this id: $id"))
    }
  }

  def deleteJob(id: String) = AuthorizedAction { implicit request =>
    jobs.get(id.toRootPath) match {
      case Some(job) =>
        jobs -= job.id
        Ok(job)
      case None => NotFound(ErrorDetail(s"No job with this id: $id"))
    }
  }

  def triggerJob(id: String) = AuthorizedAction { implicit request =>
    jobs.get(id.toRootPath) match {
      case Some(job) =>
        Ok("triggered")
      case None => NotFound(ErrorDetail(s"No job with this id: $id"))
    }
  }
}

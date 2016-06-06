package dcos.metronome.api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ ErrorDetail, Authorization, UnknownJob }
import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.{ JobInfoService, JobSpecSelector }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.JobSpec
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import mesosphere.marathon.state.PathId
import mesosphere.marathon.state.PathId._

import scala.concurrent.Future

class JobSpecController(
    jobSpecService:    JobSpecService,
    jobRunService:     JobRunService,
    jobInfoService:    JobInfoService,
    val authenticator: Authenticator,
    val authorizer:    Authorizer
) extends Authorization {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def createJob = AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
    jobSpecService.createJobSpec(request.body).map(Created(_))
  }

  def listJobs(embed: Set[Embed]) = AuthorizedAction.async { implicit request =>
    jobInfoService.selectJobs(JobSpecSelector.all, embed).map(Ok(_))
  }

  def getJob(id: PathId, embed: Set[Embed]) = AuthorizedAction.async { implicit request =>
    jobInfoService.selectJob(id, JobSpecSelector.all, embed).map {
      case Some(job) => Ok(job)
      case None      => NotFound(UnknownJob(id))
    }
  }

  def updateJob(id: PathId) = AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
    def updateJob(job: JobSpec): JobSpec = request.body.copy(schedules = job.schedules)
    jobSpecService.updateJobSpec(id, updateJob).map(Ok(_))
  }

  def deleteJob(id: PathId, stopCurrentJobRuns: Boolean) = AuthorizedAction.async { implicit request =>
    jobRunService.activeRuns(id).flatMap { runs =>
      if (runs.nonEmpty && !stopCurrentJobRuns) {
        Future.successful(Conflict(ErrorDetail("There are active job runs. Override with stopCurrentJobRuns=true")))
      } else {
        for {
          killed <- Future.sequence(runs.map(run => jobRunService.killJobRun(run.jobRun.id)))
          deleted <- jobSpecService.deleteJobSpec(id)
        } yield Ok(deleted)
      }
    }
  }
}

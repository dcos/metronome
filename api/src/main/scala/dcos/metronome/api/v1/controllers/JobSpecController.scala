package dcos.metronome.api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ Authorization, ErrorDetail, UnknownJob }
import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.JobInfoService
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.JobSpec
import dcos.metronome.{ JobSpecAlreadyExists, JobSpecDoesNotExist }
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.state.PathId
import mesosphere.marathon.state.PathId._
import play.api.mvc.Result

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
    request.authorizedAsync(CreateRunSpec) { jobSpec =>
      jobSpecService.createJobSpec(jobSpec)
        .map(Created(_))
        .recover {
          case JobSpecAlreadyExists(id) => Conflict(ErrorDetail("Job with this id already exists"))
        }
    }
  }

  def listJobs(embed: Set[Embed]) = AuthorizedAction.async { implicit request =>
    jobInfoService.selectJobs(request.selectAuthorized, embed).map(Ok(_))
  }

  def getJob(id: PathId, embed: Set[Embed]) = AuthorizedAction.async { implicit request =>
    jobInfoService.selectJob(id, request.selectAuthorized, embed).map {
      case Some(job) => Ok(job)
      case None      => NotFound(UnknownJob(id))
    }
  }

  def updateJob(id: PathId) = AuthorizedAction.async(validate.json[JobSpec]) { implicit request =>
    request.authorizedAsync(UpdateRunSpec) { jobSpec =>
      def updateJob(job: JobSpec): JobSpec = jobSpec.copy(schedules = job.schedules)
      jobSpecService.updateJobSpec(id, updateJob).map(Ok(_)).recover {
        case ex: JobSpecDoesNotExist => NotFound(UnknownJob(id))
      }
    }
  }

  def deleteJob(id: PathId, stopCurrentJobRuns: Boolean) = AuthorizedAction.async { implicit request =>
    def deleteJobSpec(jobSpec: JobSpec): Future[Result] = {
      jobRunService.activeRuns(id).flatMap { runs =>
        if (runs.nonEmpty && !stopCurrentJobRuns) {
          Future.successful(Conflict(ErrorDetail("There are active job runs. Override with stopCurrentJobRuns=true")))
        } else {
          (for {
            killed <- Future.sequence(runs.map(run => jobRunService.killJobRun(run.jobRun.id)))
            deleted <- jobSpecService.deleteJobSpec(id)
          } yield Ok(deleted)).recover {
            case ex: JobSpecDoesNotExist => NotFound(UnknownJob(id))
          }
        }
      }
    }
    jobSpecService.getJobSpec(id).flatMap {
      case Some(jobSpec) => request.authorizedAsync(DeleteRunSpec, jobSpec) { deleteJobSpec }
      case None          => Future.successful(NotFound(UnknownJob(id)))
    }
  }
}

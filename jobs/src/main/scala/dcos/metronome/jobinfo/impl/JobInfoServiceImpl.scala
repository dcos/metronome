package dcos.metronome.jobinfo.impl

import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.{ JobInfo, JobSpecSelector, JobInfoService }
import dcos.metronome.jobrun.{ StartedJobRun, JobRunService }
import dcos.metronome.jobspec.JobSpecService
import mesosphere.marathon.state.PathId

import scala.concurrent.{ ExecutionContext, Future }

class JobInfoServiceImpl(jobSpecService: JobSpecService, jobRunService: JobRunService) extends JobInfoService {

  override def selectJob(jobSpecId: PathId, selector: JobSpecSelector, embed: Set[Embed])(implicit ec: ExecutionContext): Future[Option[JobInfo]] = {
    jobSpecService.getJobSpec(jobSpecId).flatMap {
      case Some(jobSpec) if selector.matches(jobSpec) =>
        val runFuture = if (embed(Embed.ActiveRuns)) jobRunService.activeRuns(jobSpecId).map(Some(_)) else Future.successful(None)
        val specSchedules = if (embed(Embed.Schedules)) Some(jobSpec.schedules) else None
        runFuture.map(runs => Some(JobInfo(jobSpec, specSchedules, runs)))
      case _ => Future.successful(None)
    }
  }

  override def selectJobs(selector: JobSpecSelector, embed: Set[Embed])(implicit ec: ExecutionContext): Future[Iterable[JobInfo]] = {
    for {
      specs <- jobSpecService.listJobSpecs(selector.matches)
      runsIt <- if (embed(Embed.ActiveRuns)) jobRunService.listRuns(run => selector.matches(run.jobSpec)) else Future.successful(Iterable.empty[StartedJobRun])
      runs = runsIt.groupBy(_.jobRun.jobSpec.id)
    } yield {
      specs.map { spec =>
        val specRuns = runs.get(spec.id)
        val specSchedules = if (embed(Embed.Schedules)) Some(spec.schedules) else None
        JobInfo(spec, specSchedules, specRuns)
      }
    }
  }
}

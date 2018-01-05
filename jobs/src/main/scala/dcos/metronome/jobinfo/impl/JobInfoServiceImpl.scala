package dcos.metronome
package jobinfo.impl

import dcos.metronome.history.JobHistoryService
import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.{ JobInfo, JobSpecSelector, JobInfoService }
import dcos.metronome.jobrun.{ StartedJobRun, JobRunService }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobHistorySummary, JobId, JobSpec, JobHistory }

import scala.async.Async.{ async, await }
import scala.concurrent.{ ExecutionContext, Future }

class JobInfoServiceImpl(jobSpecService: JobSpecService, jobRunService: JobRunService, jobHistoryService: JobHistoryService) extends JobInfoService {

  override def selectJob(jobSpecId: JobId, selector: JobSpecSelector, embed: Set[Embed])(implicit ec: ExecutionContext): Future[Option[JobInfo]] = {
    async {
      val runOption = if (embed(Embed.ActiveRuns)) Some(await(jobRunService.activeRuns(jobSpecId))) else None
      val historyData = if (embed(Embed.History) || embed(Embed.HistorySummary)) await(jobHistoryService.statusFor(jobSpecId)).orElse(Some(JobHistory.empty(jobSpecId))) else None
      val historyOption = if (embed(Embed.History)) historyData else None
      val summaryOption = if (embed(Embed.HistorySummary)) historyData.map(JobHistorySummary.apply) else None
      await(jobSpecService.getJobSpec(jobSpecId)).filter(selector.matches).map { jobSpec =>
        JobInfo(jobSpec, schedulesOption(jobSpec, embed), runOption, historyOption, summaryOption)
      }
    }
  }

  override def selectJobs(selector: JobSpecSelector, embed: Set[Embed])(implicit ec: ExecutionContext): Future[Iterable[JobInfo]] = {
    async {
      val specs = await(jobSpecService.listJobSpecs(selector.matches))
      val allIds = specs.map(_.id).toSet
      val runs =
        if (embed(Embed.ActiveRuns))
          await(jobRunService.listRuns(run => allIds(run.jobSpec.id))).groupBy(_.jobRun.jobSpec.id)
        else
          Map.empty[JobId, Seq[StartedJobRun]]
      val availableHistories =
        if (embed(Embed.History) || embed(Embed.HistorySummary))
          await(jobHistoryService.list(history => allIds(history.jobSpecId))).map(history => history.jobSpecId -> history).toMap
        else
          Map.empty[JobId, JobHistory]
      val histories = if (embed(Embed.History)) availableHistories else Map.empty[JobId, JobHistory]
      val summaries = if (embed(Embed.HistorySummary)) availableHistories.map{ case (i, h) => i -> JobHistorySummary(h) } else Map.empty[JobId, JobHistorySummary]
      def history(id: JobId): Option[JobHistory] =
        if (embed(Embed.History)) histories.get(id).orElse(Some(JobHistory.empty(id))) else None
      def summary(id: JobId): Option[JobHistorySummary] =
        if (embed(Embed.HistorySummary)) summaries.get(id).orElse(Some(JobHistorySummary.empty(id))) else None
      specs.map { spec =>
        JobInfo(spec, schedulesOption(spec, embed), runs.get(spec.id), history(spec.id), summary(spec.id))
      }
    }
  }
  private[this] def schedulesOption(spec: JobSpec, embed: Set[Embed]) = {
    if (embed(Embed.Schedules)) Some(spec.schedules) else None
  }
}

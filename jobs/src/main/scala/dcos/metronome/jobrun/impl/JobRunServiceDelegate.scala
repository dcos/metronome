package dcos.metronome
package jobrun.impl

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import dcos.metronome.jobrun.{ JobRunConfig, JobRunService, StartedJobRun }
import dcos.metronome.model._
import mesosphere.marathon.metrics.Metrics

import scala.concurrent.Future

private[jobrun] class JobRunServiceDelegate(
  config:   JobRunConfig,
  actorRef: ActorRef,
  metrics:  Metrics) extends JobRunService {

  implicit val timeout: Timeout = config.askTimeout
  import JobRunServiceActor._

  private val listRunsTimeMetric = metrics.timer("debug.job-run.operations.list.duration")
  private val getRunTimeMetric = metrics.timer("debug.job-run.operations.get.duration")
  private val killRunTimeMetric = metrics.timer("debug.job-run.operations.kill.duration")
  private val listActiveRunsTimeMetric = metrics.timer("debug.job-run.operations.list-active.duration")
  private val startRunTimeMetric = metrics.timer("debug.job-run.operations.start.duration")

  override def listRuns(filter: JobRun => Boolean): Future[Iterable[StartedJobRun]] = listRunsTimeMetric {
    actorRef.ask(ListRuns(filter)).mapTo[Iterable[StartedJobRun]]
  }

  override def getJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]] = getRunTimeMetric {
    actorRef.ask(GetJobRun(jobRunId)).mapTo[Option[StartedJobRun]]
  }

  override def killJobRun(jobRunId: JobRunId): Future[StartedJobRun] = killRunTimeMetric {
    actorRef.ask(KillJobRun(jobRunId)).mapTo[StartedJobRun]
  }

  override def activeRuns(jobId: JobId): Future[Iterable[StartedJobRun]] = listActiveRunsTimeMetric {
    actorRef.ask(GetActiveJobRuns(jobId)).mapTo[Iterable[StartedJobRun]]
  }

  override def startJobRun(jobSpec: JobSpec, schedule: Option[ScheduleSpec] = None): Future[StartedJobRun] = startRunTimeMetric {
    actorRef.ask(TriggerJobRun(jobSpec, schedule)).mapTo[StartedJobRun]
  }
}

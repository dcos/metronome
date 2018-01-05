package dcos.metronome
package jobrun.impl

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import dcos.metronome.jobrun.{ JobRunConfig, JobRunService, StartedJobRun }
import dcos.metronome.model.{ JobId, JobRun, JobRunId, JobSpec }

import scala.concurrent.Future

private[jobrun] class JobRunServiceDelegate(
  config:   JobRunConfig,
  actorRef: ActorRef) extends JobRunService {

  implicit val timeout: Timeout = config.askTimeout
  import JobRunServiceActor._

  override def listRuns(filter: JobRun => Boolean): Future[Iterable[StartedJobRun]] = {
    actorRef.ask(ListRuns(filter)).mapTo[Iterable[StartedJobRun]]
  }

  override def getJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]] = {
    actorRef.ask(GetJobRun(jobRunId)).mapTo[Option[StartedJobRun]]
  }

  override def killJobRun(jobRunId: JobRunId): Future[StartedJobRun] = {
    actorRef.ask(KillJobRun(jobRunId)).mapTo[StartedJobRun]
  }

  override def activeRuns(jobId: JobId): Future[Iterable[StartedJobRun]] = {
    actorRef.ask(GetActiveJobRuns(jobId)).mapTo[Iterable[StartedJobRun]]
  }

  override def startJobRun(jobSpec: JobSpec): Future[StartedJobRun] = {
    actorRef.ask(TriggerJobRun(jobSpec)).mapTo[StartedJobRun]
  }
}

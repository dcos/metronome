package dcos.metronome.jobrun.impl

import akka.actor.ActorRef
import dcos.metronome.jobrun.{ JobRunConfig, JobRunService, StartedJobRun }
import dcos.metronome.model.{ JobRun, JobRunId, JobSpec }
import mesosphere.marathon.core.task.bus.TaskChangeObservables.TaskChanged
import mesosphere.marathon.state.PathId

import scala.concurrent.{ Future, Promise }

private[jobrun] class JobRunServiceDelegate(
    config:   JobRunConfig,
    actorRef: ActorRef
) extends JobRunService {

  import JobRunServiceActor._

  override def listRuns(filter: JobRun => Boolean): Future[Iterable[StartedJobRun]] = {
    val promise = Promise[Iterable[StartedJobRun]]
    actorRef ! ListRuns(promise)
    promise.future
  }

  override def getJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]] = {
    val promise = Promise[Option[StartedJobRun]]
    actorRef ! GetJobRun(jobRunId, promise)
    promise.future
  }

  override def killJobRun(jobRunId: JobRunId): Future[StartedJobRun] = {
    val promise = Promise[StartedJobRun]
    actorRef ! KillJobRun(jobRunId, promise)
    promise.future
  }

  override def activeRuns(jobId: PathId): Future[Iterable[StartedJobRun]] = {
    val promise = Promise[Iterable[StartedJobRun]]
    actorRef ! GetActiveJobRuns(jobId, promise)
    promise.future
  }

  override def startJobRun(jobSpec: JobSpec): Future[StartedJobRun] = {
    val promise = Promise[StartedJobRun]
    actorRef ! TriggerJobRun(jobSpec, promise)
    promise.future
  }
}

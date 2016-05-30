package dcos.metronome.jobrun.impl

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import dcos.metronome.jobrun.{ JobRunConfig, StartedJobRun, JobRunService }
import dcos.metronome.model.{ JobRun, JobRunId, JobSpec }
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

private[jobrun] class JobRunServiceDelegate(
    config:   JobRunConfig,
    actorRef: ActorRef @@ JobRunService
) extends JobRunService {

  override def listRuns(filter: JobRun => Boolean): Future[Seq[StartedJobRun]] = ???

  override def getJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]] = ???

  override def killJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]] = ???

  override def activeRuns(jobId: PathId): Future[Seq[StartedJobRun]] = ???

  override def startJobRun(jobSpec: JobSpec): Future[StartedJobRun] = ???
}

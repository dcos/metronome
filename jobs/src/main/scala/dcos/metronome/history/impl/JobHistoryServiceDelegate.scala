package dcos.metronome.history.impl

import akka.actor.ActorRef
import dcos.metronome.history.JobHistoryService
import dcos.metronome.model.JobHistory
import mesosphere.marathon.state.PathId

import scala.concurrent.{ Future, Promise }

class JobHistoryServiceDelegate(actorRef: ActorRef) extends JobHistoryService {
  import JobHistoryServiceActor._

  override def statusFor(jobSpecId: PathId): Future[Option[JobHistory]] = {
    val promise = Promise[Option[JobHistory]]
    actorRef ! GetJobStatus(jobSpecId, promise)
    promise.future
  }

  override def list(filter: (JobHistory) => Boolean): Future[Iterable[JobHistory]] = {
    val promise = Promise[Iterable[JobHistory]]
    actorRef ! ListJobStatus(filter, promise)
    promise.future
  }
}

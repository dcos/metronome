package dcos.metronome.history.impl

import akka.actor.ActorRef
import dcos.metronome.history.JobHistoryService
import dcos.metronome.model.{ JobId, JobHistory }

import scala.concurrent.{ Future, Promise }

class JobHistoryServiceDelegate(actorRef: ActorRef) extends JobHistoryService {
  import JobHistoryServiceActor._

  override def statusFor(jobSpecId: JobId): Future[Option[JobHistory]] = {
    val promise = Promise[Option[JobHistory]]
    actorRef ! GetJobHistory(jobSpecId, promise)
    promise.future
  }

  override def list(filter: (JobHistory) => Boolean): Future[Iterable[JobHistory]] = {
    val promise = Promise[Iterable[JobHistory]]
    actorRef ! ListJobHistories(filter, promise)
    promise.future
  }
}

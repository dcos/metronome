package dcos.metronome.history.impl

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import dcos.metronome.history.{ JobHistoryConfig, JobHistoryService }
import dcos.metronome.model.{ JobId, JobHistory }

import scala.concurrent.Future

class JobHistoryServiceDelegate(actorRef: ActorRef, config: JobHistoryConfig) extends JobHistoryService {
  import JobHistoryServiceActor._
  implicit val timeout: Timeout = config.askTimeout

  override def statusFor(jobSpecId: JobId): Future[Option[JobHistory]] = {
    actorRef.ask(GetJobHistory(jobSpecId)).mapTo[Option[JobHistory]]
  }

  override def list(filter: (JobHistory) => Boolean): Future[Iterable[JobHistory]] = {
    actorRef.ask(ListJobHistories(filter)).mapTo[Iterable[JobHistory]]
  }
}

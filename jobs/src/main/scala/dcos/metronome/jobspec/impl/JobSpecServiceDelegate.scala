package dcos.metronome
package jobspec.impl

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import dcos.metronome.jobspec.impl.JobSpecServiceActor._
import dcos.metronome.jobspec.{ JobSpecConfig, JobSpecService }
import dcos.metronome.model.{ JobId, JobSpec }

import scala.concurrent.Future

class JobSpecServiceDelegate(
  config:   JobSpecConfig,
  actorRef: ActorRef) extends JobSpecService {

  implicit val timeout: Timeout = config.askTimeout

  override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] = {
    actorRef.ask(CreateJobSpec(jobSpec)).mapTo[JobSpec]
  }

  override def getJobSpec(id: JobId): Future[Option[JobSpec]] = {
    actorRef.ask(GetJobSpec(id)).mapTo[Option[JobSpec]]
  }

  override def updateJobSpec(id: JobId, update: JobSpec => JobSpec): Future[JobSpec] = {
    actorRef.ask(UpdateJobSpec(id, update)).mapTo[JobSpec]
  }

  override def listJobSpecs(filter: JobSpec => Boolean): Future[Iterable[JobSpec]] = {
    actorRef.ask(ListJobSpecs(filter)).mapTo[Iterable[JobSpec]]
  }

  override def deleteJobSpec(id: JobId): Future[JobSpec] = {
    actorRef.ask(DeleteJobSpec(id)).mapTo[JobSpec]
  }
}

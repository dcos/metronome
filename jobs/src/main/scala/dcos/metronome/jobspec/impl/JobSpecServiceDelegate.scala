package dcos.metronome.jobspec.impl

import akka.actor.ActorRef
import dcos.metronome.jobspec.impl.JobSpecServiceActor._
import dcos.metronome.jobspec.{ JobSpecConfig, JobSpecService }
import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId

import scala.concurrent.{ Promise, Future }

class JobSpecServiceDelegate(
    config:   JobSpecConfig,
    actorRef: ActorRef
) extends JobSpecService {

  override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] = {
    val promise = Promise[JobSpec]
    actorRef ! CreateJobSpec(jobSpec, promise)
    promise.future
  }

  override def getJobSpec(id: PathId): Future[Option[JobSpec]] = {
    val promise = Promise[Option[JobSpec]]
    actorRef ! GetJobSpec(id, promise)
    promise.future
  }

  override def updateJobSpec(id: PathId, update: (JobSpec) => JobSpec): Future[JobSpec] = {
    val promise = Promise[JobSpec]
    actorRef ! UpdateJobSpec(id, update, promise)
    promise.future
  }

  override def listJobSpecs(filter: (JobSpec) => Boolean): Future[Iterable[JobSpec]] = {
    val promise = Promise[Iterable[JobSpec]]
    actorRef ! ListJobSpecs(filter, promise)
    promise.future
  }

  override def deleteJobSpec(id: PathId): Future[JobSpec] = {
    val promise = Promise[JobSpec]
    actorRef ! DeleteJobSpec(id, promise)
    promise.future
  }
}

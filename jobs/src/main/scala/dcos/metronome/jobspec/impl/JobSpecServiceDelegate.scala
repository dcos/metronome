package dcos.metronome.jobspec.impl

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import dcos.metronome.jobspec.{ JobSpecConfig, JobSpecService }
import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

class JobSpecServiceDelegate(
    config:   JobSpecConfig,
    actorRef: ActorRef @@ JobSpecService
) extends JobSpecService {

  override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] = ???

  override def getJobSpec(id: PathId): Future[Option[JobSpec]] = ???

  override def updateJobSpec(id: PathId, update: (JobSpec) => JobSpec): Future[Option[JobSpec]] = ???

  override def getAllJobs: Future[Seq[JobSpec]] = ???

  override def deleteJobSpec(id: PathId): Future[Option[JobSpec]] = ???
}

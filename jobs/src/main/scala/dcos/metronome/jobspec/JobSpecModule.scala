package dcos.metronome.jobspec

import akka.actor.ActorSystem
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.impl.{ JobSpecSchedulerActor, JobSpecPersistenceActor, JobSpecServiceDelegate, JobSpecServiceActor }
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.{ Repository, RepositoryModule }
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.state.PathId

class JobSpecModule(
    config:            JobSpecConfig,
    actorSystem:       ActorSystem,
    clock:             Clock,
    jobSpecRepository: Repository[PathId, JobSpec],
    runService:        JobRunService
) {

  private[this] def persistenceActor(id: PathId) = JobSpecPersistenceActor.props(id, jobSpecRepository)
  private[this] def scheduleActor(jobSpec: JobSpec) = JobSpecSchedulerActor.props(jobSpec, clock, runService)
  //TODO: start when we get elected
  val serviceActor = actorSystem.actorOf(JobSpecServiceActor.props(jobSpecRepository, persistenceActor, scheduleActor))

  def jobSpecService: JobSpecService = new JobSpecServiceDelegate(config, serviceActor)

}

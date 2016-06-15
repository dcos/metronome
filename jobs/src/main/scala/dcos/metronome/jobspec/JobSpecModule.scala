package dcos.metronome.jobspec

import akka.actor.ActorSystem
import dcos.metronome.behavior.Behavior
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.impl.{ JobSpecPersistenceActor, JobSpecSchedulerActor, JobSpecServiceActor, JobSpecServiceDelegate }
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.state.PathId

class JobSpecModule(
    config:            JobSpecConfig,
    actorSystem:       ActorSystem,
    clock:             Clock,
    jobSpecRepository: Repository[PathId, JobSpec],
    runService:        JobRunService,
    behavior:          Behavior,
    leadershipModule:  LeadershipModule
) {

  private[this] def persistenceActor(id: PathId) = JobSpecPersistenceActor.props(id, jobSpecRepository, behavior)
  private[this] def scheduleActor(jobSpec: JobSpec) = JobSpecSchedulerActor.props(jobSpec, clock, runService, behavior)

  val serviceActor = leadershipModule.startWhenLeader(
    JobSpecServiceActor.props(jobSpecRepository, persistenceActor, scheduleActor, behavior), "JobSpecServiceActor"
  )

  def jobSpecService: JobSpecService = behavior(new JobSpecServiceDelegate(config, serviceActor))
}

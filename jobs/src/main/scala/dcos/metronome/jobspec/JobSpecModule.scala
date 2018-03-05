package dcos.metronome
package jobspec

import java.time.Clock

import akka.actor.ActorSystem
import dcos.metronome.behavior.Behavior
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.impl.{ JobSpecPersistenceActor, JobSpecSchedulerActor, JobSpecServiceActor, JobSpecServiceDelegate }
import dcos.metronome.model.{ JobId, JobSpec }
import dcos.metronome.repository.Repository
import mesosphere.marathon.core.leadership.LeadershipModule

class JobSpecModule(
  config:            JobSpecConfig,
  actorSystem:       ActorSystem,
  clock:             Clock,
  jobSpecRepository: Repository[JobId, JobSpec],
  runService:        JobRunService,
  behavior:          Behavior,
  leadershipModule:  LeadershipModule) {

  private[this] def persistenceActor(id: JobId) = JobSpecPersistenceActor.props(id, jobSpecRepository, behavior)
  private[this] def scheduleActor(jobSpec: JobSpec) = JobSpecSchedulerActor.props(jobSpec, clock, runService, behavior)

  val serviceActor = leadershipModule.startWhenLeader(
    JobSpecServiceActor.props(jobSpecRepository, persistenceActor, scheduleActor, behavior), "JobSpecServiceActor")

  def jobSpecService: JobSpecService = behavior(new JobSpecServiceDelegate(config, serviceActor))
}

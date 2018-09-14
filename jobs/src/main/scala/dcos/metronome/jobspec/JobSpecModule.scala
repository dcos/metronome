package dcos.metronome
package jobspec

import java.time.Clock

import akka.actor.ActorSystem
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.impl.{ JobSpecPersistenceActor, JobSpecSchedulerActor, JobSpecServiceActor, JobSpecServiceDelegate }
import dcos.metronome.model.{ JobId, JobSpec }
import dcos.metronome.repository.Repository
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.metrics.Metrics

class JobSpecModule(
  config:            JobSpecConfig,
  actorSystem:       ActorSystem,
  clock:             Clock,
  jobSpecRepository: Repository[JobId, JobSpec],
  runService:        JobRunService,
  metrics:           Metrics,
  leadershipModule:  LeadershipModule) {

  private[this] def persistenceActor(id: JobId) = JobSpecPersistenceActor.props(id, jobSpecRepository, metrics)
  private[this] def scheduleActor(jobSpec: JobSpec) = JobSpecSchedulerActor.props(jobSpec, clock, runService)

  val serviceActor = leadershipModule.startWhenLeader(
    JobSpecServiceActor.props(jobSpecRepository, persistenceActor, scheduleActor), "JobSpecServiceActor")

  def jobSpecService: JobSpecService = new JobSpecServiceDelegate(config, serviceActor, metrics)
}

package dcos.metronome
package jobspec

import java.time.Clock

import akka.actor.ActorSystem
import dcos.metronome.measurement.ServiceMeasurement
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
  measured:          ServiceMeasurement,
  leadershipModule:  LeadershipModule) {

  private[this] def persistenceActor(id: JobId) = JobSpecPersistenceActor.props(id, jobSpecRepository, measured)
  private[this] def scheduleActor(jobSpec: JobSpec) = JobSpecSchedulerActor.props(jobSpec, clock, runService, measured)

  val serviceActor = leadershipModule.startWhenLeader(
    JobSpecServiceActor.props(jobSpecRepository, persistenceActor, scheduleActor, measured), "JobSpecServiceActor")

  def jobSpecService: JobSpecService = measured(new JobSpecServiceDelegate(config, serviceActor))
}

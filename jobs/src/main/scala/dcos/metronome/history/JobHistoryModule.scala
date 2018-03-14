package dcos.metronome
package history

import java.time.Clock

import akka.actor.{ ActorRef, ActorSystem }
import dcos.metronome.measurement.MethodMeasurement
import dcos.metronome.history.impl.{ JobHistoryServiceActor, JobHistoryServiceDelegate }
import dcos.metronome.model.{ JobHistory, JobId }
import dcos.metronome.repository.Repository
import mesosphere.marathon.core.leadership.LeadershipModule

class JobHistoryModule(
  config:           JobHistoryConfig,
  actorSystem:      ActorSystem,
  clock:            Clock,
  repository:       Repository[JobId, JobHistory],
  measured:         MethodMeasurement,
  leadershipModule: LeadershipModule) {

  lazy val jobHistoryServiceActor: ActorRef = leadershipModule.startWhenLeader(
    JobHistoryServiceActor.props(config, clock, repository, measured), "JobHistoryServiceActor")

  lazy val jobHistoryService: JobHistoryService = measured(new JobHistoryServiceDelegate(jobHistoryServiceActor, config))
}

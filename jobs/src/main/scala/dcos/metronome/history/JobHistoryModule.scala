package dcos.metronome.history

import akka.actor.{ ActorSystem, ActorRef }
import dcos.metronome.behavior.Behavior
import dcos.metronome.history.impl.{ JobHistoryServiceDelegate, JobHistoryServiceActor }
import dcos.metronome.model.{ JobId, JobHistory }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.leadership.LeadershipModule

class JobHistoryModule(
    config:           JobHistoryConfig,
    actorSystem:      ActorSystem,
    clock:            Clock,
    repository:       Repository[JobId, JobHistory],
    behavior:         Behavior,
    leadershipModule: LeadershipModule
) {

  lazy val jobHistoryServiceActor: ActorRef = leadershipModule.startWhenLeader(
    JobHistoryServiceActor.props(config, clock, repository, behavior), "JobHistoryServiceActor"
  )

  lazy val jobHistoryService: JobHistoryService = behavior(new JobHistoryServiceDelegate(jobHistoryServiceActor, config))
}

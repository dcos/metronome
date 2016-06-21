package dcos.metronome.history

import akka.actor.{ ActorSystem, ActorRef }
import dcos.metronome.behavior.Behavior
import dcos.metronome.history.impl.{ JobHistoryServiceDelegate, JobHistoryServiceActor }
import dcos.metronome.model.{ JobId, JobHistory }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock

class JobHistoryModule(
    config:      JobHistoryConfig,
    actorSystem: ActorSystem,
    clock:       Clock,
    repository:  Repository[JobId, JobHistory],
    behavior:    Behavior
) {

  //TODO: start when elected
  lazy val jobHistoryServiceActor: ActorRef = actorSystem.actorOf(JobHistoryServiceActor.props(config, clock, repository, behavior))

  lazy val jobHistoryService: JobHistoryService = behavior(new JobHistoryServiceDelegate(jobHistoryServiceActor))
}

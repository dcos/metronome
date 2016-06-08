package dcos.metronome.history

import akka.actor.{ ActorSystem, ActorRef }
import dcos.metronome.history.impl.{ JobHistoryServiceDelegate, JobHistoryServiceActor }
import dcos.metronome.model.JobHistory
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.state.PathId

class JobHistoryModule(config: JobHistoryConfig, actorSystem: ActorSystem, clock: Clock, repository: Repository[PathId, JobHistory]) {

  //TODO: start when elected
  lazy val jobHistoryServiceActor: ActorRef = actorSystem.actorOf(JobHistoryServiceActor.props(config, clock, repository))

  lazy val jobHistoryService: JobHistoryService = new JobHistoryServiceDelegate(jobHistoryServiceActor)
}

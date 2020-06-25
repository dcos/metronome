package dcos.metronome
package history

import java.time.Clock

import akka.actor.{ActorRef, ActorSystem}
import dcos.metronome.history.impl.{JobHistoryServiceActor, JobHistoryServiceDelegate}
import dcos.metronome.model.{JobHistory, JobId}
import dcos.metronome.repository.Repository
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.metrics.Metrics

class JobHistoryModule(
    config: JobHistoryConfig,
    actorSystem: ActorSystem,
    clock: Clock,
    repository: Repository[JobId, JobHistory],
    metrics: Metrics,
    leadershipModule: LeadershipModule
) {

  val jobHistoryServiceActor: ActorRef = leadershipModule.startWhenLeader(
    JobHistoryServiceActor.props(config, clock, repository, metrics),
    "JobHistoryServiceActor"
  )

  lazy val jobHistoryService: JobHistoryService = new JobHistoryServiceDelegate(jobHistoryServiceActor, config)
}

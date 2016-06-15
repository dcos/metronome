package dcos.metronome.jobrun

import akka.actor.{ ActorContext, ActorSystem, Props }
import dcos.metronome.behavior.Behavior
import dcos.metronome.jobrun.impl.{ JobRunExecutorActor, JobRunPersistenceActor, JobRunServiceActor, JobRunServiceDelegate }
import dcos.metronome.model.{ JobResult, JobRun, JobRunId }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.leadership.{ LeadershipModule, LeadershipCoordinator }

import scala.concurrent.Promise

class JobRunModule(
    config:           JobRunConfig,
    actorSystem:      ActorSystem,
    clock:            Clock,
    jobRunRepository: Repository[JobRunId, JobRun],
    launchQueue:      LaunchQueue,
    driverHolder:     MarathonSchedulerDriverHolder,
    behavior:         Behavior,
    leadershipModule: LeadershipModule
) {

  import com.softwaremill.macwire._

  private[this] def executorFactory(jobRun: JobRun, promise: Promise[JobResult]): Props = {
    val persistenceActorFactory = (id: JobRunId, context: ActorContext) =>
      context.actorOf(JobRunPersistenceActor.props(id, jobRunRepository, behavior))
    JobRunExecutorActor.props(jobRun, promise, persistenceActorFactory, launchQueue, driverHolder, clock, behavior)
  }

  val jobRunServiceActor = leadershipModule.startWhenLeader(
    JobRunServiceActor.props(clock, executorFactory, jobRunRepository, behavior), "JobRunService"
  )

  def jobRunService: JobRunService = behavior(wire[JobRunServiceDelegate])
}

package dcos.metronome.jobrun

import akka.actor.{ ActorContext, ActorSystem, Props }
import dcos.metronome.jobrun.impl.{ JobRunExecutorActor, JobRunPersistenceActor, JobRunServiceActor, JobRunServiceDelegate }
import dcos.metronome.model.{ JobResult, JobRun, JobRunId }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue

import scala.concurrent.Promise

class JobRunModule(
    config:           JobRunConfig,
    actorSystem:      ActorSystem,
    clock:            Clock,
    jobRunRepository: Repository[JobRunId, JobRun],
    launchQueue:      LaunchQueue,
    driverHolder:     MarathonSchedulerDriverHolder
) {

  private[this] def executorFactory(jobRun: JobRun, promise: Promise[JobResult]): Props = {
    val persistenceActorFactory = (id: JobRunId, context: ActorContext) =>
      context.actorOf(JobRunPersistenceActor.props(id, jobRunRepository))
    JobRunExecutorActor.props(jobRun, promise, persistenceActorFactory, launchQueue, driverHolder, clock)
  }

  //TODO: Start when we get elected
  private[this] val jobRunServiceActor = actorSystem.actorOf(
    JobRunServiceActor.props(clock, executorFactory, jobRunRepository)
  )

  def jobRunService: JobRunService = new JobRunServiceDelegate(config, jobRunServiceActor)
}

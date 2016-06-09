package dcos.metronome.jobrun

import akka.actor.{ ActorSystem, Props }
import dcos.metronome.jobrun.impl.{ JobRunExecutorActor, JobRunServiceActor, JobRunServiceDelegate }
import dcos.metronome.model.{ JobResult, JobRun, JobRunId }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.launchqueue.LaunchQueue

import scala.concurrent.Promise

class JobRunModule(
    config:           JobRunConfig,
    actorSystem:      ActorSystem,
    clock:            Clock,
    jobRunRepository: Repository[JobRunId, JobRun],
    launchQueue:      LaunchQueue
) {

  private[this] def executorFactory(jobRun: JobRun, promise: Promise[JobResult]): Props = {
    //TODO: remove repo, but add a repo actor factory
    JobRunExecutorActor.props(jobRun, promise, jobRunRepository, launchQueue)
  }

  //TODO: Start when we get elected
  private[this] val jobRunServiceActor = actorSystem.actorOf(
    JobRunServiceActor.props(clock, executorFactory, jobRunRepository)
  )

  def jobRunService: JobRunService = new JobRunServiceDelegate(config, jobRunServiceActor)
}
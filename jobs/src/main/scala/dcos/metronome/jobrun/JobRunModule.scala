package dcos.metronome.jobrun

import akka.actor.{ Props, ActorSystem }
import dcos.metronome.jobrun.impl.{ JobRunExecutorActor, JobRunServiceActor, JobRunServiceDelegate }
import dcos.metronome.model.{ JobResult, JobRunId, JobRun }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.time.Clock

import scala.concurrent.Promise

class JobRunModule(
    config:           JobRunConfig,
    actorSystem:      ActorSystem,
    clock:            Clock,
    jobRunRepository: Repository[JobRunId, JobRun]
) {

  private[this] def executorFactory(jobRun: JobRun, promise: Promise[JobResult]): Props = {
    JobRunExecutorActor.props(jobRun, promise, jobRunRepository)
  }

  //TODO: Start when we get elected
  private[this] val jobRunServiceActor = actorSystem.actorOf(
    JobRunServiceActor.props(clock, executorFactory, jobRunRepository)
  )

  def jobRunService: JobRunService = new JobRunServiceDelegate(config, jobRunServiceActor)
}

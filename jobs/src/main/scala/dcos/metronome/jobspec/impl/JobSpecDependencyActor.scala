package dcos.metronome.jobspec.impl

import akka.actor.{Actor, ActorLogging, Props, Stash}
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.{Event, JobSpec}

/**
  * Manages one JobSpec.
  *
  * If the JobSpec has dependencies, it subscribes to the events of its dependencies, ie parents.
  *
  * This actor is analog to [[JobSpecSchedulerActor]].
  *
  * TODO: find a better name
  */
class JobSpecDependencyActor(initSpec: JobSpec, runService: JobRunService) extends Actor with Stash with ActorLogging {

  val dependencyIndex = initSpec.dependencies.toSet

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[Event.JobRunFinished])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case Event.JobRunFinished(jobRun, _, _) if dependencyIndex.contains(jobRun.jobSpec.id) =>
      // TODO: account for all dependencies. Also make sure that the finished job run is from the same frame.
      runService.startJobRun(initSpec)

    case Event.JobRunFailed(jobRun, _, _) if dependencyIndex.contains(jobRun.jobSpec.id) =>
      ???

    case Event.JobRunStarted(jobRun, _, _) if dependencyIndex.contains(jobRun.jobSpec.id) =>
      ???
  }
}

object JobSpecDependencyActor {

  def props(spec: JobSpec, runService: JobRunService): Props = {
    Props(new JobSpecDependencyActor(spec, runService))
  }
}

package dcos.metronome.jobrun.impl

import akka.actor.{ Actor, ActorLogging, Props }
import dcos.metronome.JobRunFailed
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus }
import dcos.metronome.repository.Repository
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.event.MesosStatusUpdateEvent
import mesosphere.marathon.state.RunSpec
import org.apache.mesos

import scala.concurrent.Promise
import scala.util.Try

/**
  * Handles one job run from start until the job either completes successful or failed.
  *
  * @param run the related job run  object.
  */
class JobRunExecutorActor(
    run:         JobRun,
    promise:     Promise[JobResult],
    repository:  Repository[JobRunId, JobRun],
    launchQueue: LaunchQueue
) extends Actor with ActorLogging {
  import JobRunExecutorActor._
  import JobRunPersistenceActor._

  lazy val persistenceActor = context.actorOf(JobRunPersistenceActor.props(run.id, repository))
  var jobRun: JobRun = run

  def runSpec: RunSpec = {
    import dcos.metronome.utils.glue.MarathonImplicits._
    jobRun
  }

  override def preStart(): Unit = {
    jobRun.status match {
      case JobRunStatus.Starting => createJob()
      case JobRunStatus.Active   => reconcileRunningJob()
      case _                     => killCurrentRun()
    }
  }

  override def postStop(): Unit = {
    log.debug("Stopped {}", self)
  }

  override def receive: Receive = {
    case KillCurrentJobRun              => killCurrentRun()
    case JobRunCreated(_, persisted, _) => startJob(persisted)
    case JobRunUpdated(_, persisted, _) => log.debug(s"JobRun ${persisted.id} has been persisted")
    case JobRunDeleted(_, persisted, _) => jobRunFinished()
    case PersistFailed(_, id, ex, _)    => jobRunAborted()

    case ForwardStatusUpdate(update)    => taskChanged(update)
  }

  def createJob(): Unit = {
    log.info(s"Create JobRun ${jobRun.id}")
    persistenceActor ! Create(jobRun)
  }

  def startJob(run: JobRun): Unit = {
    jobRun = run
    log.info(s"Execution of JobRun ${jobRun.id} has been started")

    launchQueue.add(runSpec, count = 1)
  }

  def updateJob(changed: JobRun): Unit = {
    jobRun = changed
    persistenceActor ! Update(_ => changed)
    context.parent ! JobRunUpdate(StartedJobRun(changed, promise.future))
  }

  def jobRunFinished() = {
    log.info(s"Execution of JobRun ${jobRun.id} has been finished")
    val result = JobResult(jobRun)
    context.parent ! JobRunFinished(result)
    promise.success(result)
  }

  def jobRunAborted() = {
    log.info(s"Execution of JobRun ${jobRun.id} has been aborted")
    val result = JobResult(jobRun)
    context.parent ! JobRunAborted(result)
    promise.failure(JobRunFailed(result))
  }

  def reconcileRunningJob(): Unit = {
    //TODO: fill reconciling logic here
  }

  def killCurrentRun(): Unit = {
    log.info(s"Kill execution of JobRun ${jobRun.id}")
    // TODO: fill aborting logic here.

    cleanup()
    // TODO: we should switch to another receiver here to handle PersistFailed different
  }

  object ActiveTaskStatus {
    def unapply(status: mesos.Protos.TaskStatus): Option[mesos.Protos.TaskStatus] = {
      import mesos.Protos.TaskState._
      status.getState match {
        case TASK_STARTING | TASK_STAGING | TASK_RUNNING => Some(status)
        case _ => None
      }
    }
  }

  def taskChanged(update: MesosStatusUpdateEvent): Unit = {
    // for now this is a simple and naive implementation
    import TaskStates._
    log.debug("processing {}", update)

    Try { mesos.Protos.TaskState.valueOf(update.taskStatus) } foreach {
      case state: mesos.Protos.TaskState if active(state) && jobRun.status != JobRunStatus.Active =>
        log.info("Run is now active: {}", run.id)
        updateJob(jobRun.copy(status = JobRunStatus.Active))

      case state: mesos.Protos.TaskState if finished(state) =>
        log.info("Run finished: {}", run.id)
        cleanup()

      case state: mesos.Protos.TaskState if failed(state) =>
        log.info("Run failed: {}", run.id)
        cleanup()

      case state: mesos.Protos.TaskState =>
        log.debug("Ignoring {}", state)
    }

  }

  def cleanup(): Unit = {
    launchQueue.purge(runSpec.id)
    persistenceActor ! Delete(jobRun)
  }

}

object JobRunExecutorActor {

  case object KillCurrentJobRun
  case class JobRunUpdate(startedJobRun: StartedJobRun)

  case class JobRunFinished(jobResult: JobResult)
  case class JobRunAborted(jobResult: JobResult)

  case class ForwardStatusUpdate(update: MesosStatusUpdateEvent)

  def props(
    run:         JobRun,
    promise:     Promise[JobResult],
    repository:  Repository[JobRunId, JobRun],
    launchQueue: LaunchQueue
  ): Props = Props(
    new JobRunExecutorActor(run, promise, repository, launchQueue)
  )
}

object TaskStates {
  import mesos.Protos.TaskState._

  val active = {
    Set(TASK_STAGING, TASK_STARTING, TASK_RUNNING)
  }
  val failed = {
    import mesos.Protos.TaskState._
    Set(TASK_ERROR, TASK_FAILED, TASK_LOST)
  }
  def finished(state: mesos.Protos.TaskState): Boolean = state == mesos.Protos.TaskState.TASK_FINISHED
}

package dcos.metronome.jobrun.impl

import akka.actor.{ Actor, ActorLogging, Props }
import dcos.metronome.JobRunFailed
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus }
import dcos.metronome.repository.Repository
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.TaskStateOp
import mesosphere.marathon.core.task.bus.MarathonTaskStatus
import mesosphere.marathon.core.task.bus.TaskChangeObservables.TaskChanged
import mesosphere.marathon.state.RunSpec
import org.apache.mesos

import scala.concurrent.Promise

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
    case KillCurrentJobRun                             => killCurrentRun()
    case JobRunCreated(_, persisted, _)                => startJob(persisted)
    case JobRunUpdated(_, persisted, _)                => log.debug(s"JobRun ${persisted.id} has been persisted")
    case JobRunDeleted(_, persisted, _)                => jobRunFinished()
    case PersistFailed(_, id, ex, _)                   => jobRunAborted()

    case TaskChangedUpdate(taskChanged, updatePromise) => receiveTaskUpdate(taskChanged, updatePromise)
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

  def receiveTaskUpdate(taskChanged: TaskChanged, updatePromise: Promise[Unit]): Unit = {
    // for now this is a simple and naive implementation
    import TaskStateOp.MesosUpdate
    import mesosphere.marathon.core.task.bus.MarathonTaskStatus._

    val stateOp = taskChanged.stateOp
    stateOp match {
      case MesosUpdate(_, Active(_), _) if jobRun.status != JobRunStatus.Active =>
        log.info("Run is now active: {}", run.id)
        updateJob(jobRun.copy(status = JobRunStatus.Active))

      case MesosUpdate(_, Finished(_), _) =>
        log.info("Run finished: {}", run.id)
        cleanup()

      case MesosUpdate(_, Terminal(_), _) =>
        log.info("Run failed: {}", run.id)
        cleanup()

      case _ =>
        log.debug("Ignoring taskChanged")
    }

    updatePromise.success(())
  }

  def cleanup(): Unit = {
    launchQueue.purge(runSpec.id)
    persistenceActor ! Delete(jobRun)
  }

  object Active {
    private[this] val ActiveStates = Set(
      mesos.Protos.TaskState.TASK_STAGING,
      mesos.Protos.TaskState.TASK_STARTING,
      mesos.Protos.TaskState.TASK_RUNNING
    )
    def unapply(arg: MarathonTaskStatus): Option[MarathonTaskStatus] = arg.mesosStatus match {
      case Some(taskStatus) if ActiveStates(taskStatus.getState) => Some(arg)
      case _ => None
    }
  }

}

object JobRunExecutorActor {

  case object KillCurrentJobRun
  case class JobRunUpdate(startedJobRun: StartedJobRun)

  case class JobRunFinished(jobResult: JobResult)
  case class JobRunAborted(jobResult: JobResult)

  case class TaskChangedUpdate(taskChanged: TaskChanged, promise: Promise[Unit])

  def props(
    run:         JobRun,
    promise:     Promise[JobResult],
    repository:  Repository[JobRunId, JobRun],
    launchQueue: LaunchQueue
  ): Props = Props(
    new JobRunExecutorActor(run, promise, repository, launchQueue)
  )
}

package dcos.metronome.jobrun.impl

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorRef, Props }
import dcos.metronome.JobRunFailed
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus, JobRunTask, RestartPolicy }
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.event.MesosStatusUpdateEvent
import mesosphere.marathon.state.RunSpec
import org.apache.mesos
import org.joda.time.DateTime

import scala.concurrent.Promise
import scala.util.Try

/**
  * Handles one job run from start until the job either completes successful or failed.
  *
  * @param run the related job run object.
  */
class JobRunExecutorActor(
    run:                        JobRun,
    promise:                    Promise[JobResult],
    persistenceActorRefFactory: (JobRunId, ActorContext) => ActorRef,
    launchQueue:                LaunchQueue,
    driverHolder:               MarathonSchedulerDriverHolder,
    clock:                      Clock,
    val behavior:               Behavior
) extends Actor with ActorLogging with ActorBehavior {
  import JobRunExecutorActor._
  import JobRunPersistenceActor._
  import TaskStates._

  lazy val persistenceActor = persistenceActorRefFactory(run.id, context)
  var jobRun: JobRun = run

  def runSpec: RunSpec = {
    import dcos.metronome.utils.glue.MarathonImplicits._
    jobRun
  }

  override def preStart(): Unit = {
    jobRun.status match {
      case JobRunStatus.Starting =>
        becomeCreating()

      case JobRunStatus.Active =>
        reconcileAndBecomeActive()

      case _ =>
        becomeAborting()
    }
  }

  // Transitions

  def becomeCreating(): Unit = {
    log.info(s"Create JobRun ${jobRun.id} - become creating")
    persistenceActor ! Create(jobRun)
    context.become(creating)
  }

  def becomeStarting(run: JobRun): Unit = {
    jobRun = run
    log.info(s"Execution of JobRun ${jobRun.id} has been started - become starting")
    launchQueue.add(runSpec, count = 1)

    context.become(starting)
  }

  def reconcileAndBecomeActive(): Unit = {
    //TODO: fill reconciling logic here
    context.become(active)
  }

  def becomeActive(update: MesosStatusUpdateEvent): Unit = {
    jobRun = jobRun.copy(status = JobRunStatus.Active, tasks = updatedTasks(update))
    persistenceActor ! Update(_ => jobRun)
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))

    log.debug("become active")
    context.become(active)
  }

  def updatedTasks(update: MesosStatusUpdateEvent): Map[Task.Id, JobRunTask] = {
    val updatedTask = jobRun.tasks.get(update.taskId).map { t =>
      t.copy(completedAt = Some(DateTime.parse(update.timestamp)))
    }.getOrElse {
      JobRunTask(
        id = update.taskId,
        startedAt = DateTime.parse(update.timestamp),
        completedAt = None,
        status = update.taskStatus
      )
    }

    jobRun.tasks + (updatedTask.id -> updatedTask)
  }

  def becomeFinishing(update: MesosStatusUpdateEvent): Unit = {
    launchQueue.purge(runSpec.id)
    jobRun = jobRun.copy(
      status = JobRunStatus.Success,
      tasks = updatedTasks(update),
      finishedAt = Some(DateTime.parse(update.timestamp))
    )
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.debug("become finishing")
    context.become(finishing)
  }

  def continueOrBecomeFailing(update: MesosStatusUpdateEvent): Unit = {
    def inTime: Boolean = jobRun.jobSpec.run.restart.activeDeadline.fold(true) { deadline =>
      jobRun.createdAt.plus(deadline.toMillis).getMillis > clock.now().getMillis
    }
    jobRun.jobSpec.run.restart.policy match {
      case RestartPolicy.OnFailure if inTime =>
        log.info("still in time, launching another task")
        launchQueue.add(runSpec, 1)

      case _ =>
        launchQueue.purge(runSpec.id)
        jobRun = jobRun.copy(status = JobRunStatus.Failed, tasks = updatedTasks(update))
        persistenceActor ! Delete(jobRun)

        log.info("become failing")
        context.become(failing)
    }
  }

  def becomeAborting(): Unit = {
    log.info(s"Execution of JobRun ${jobRun.id} has been aborted")
    // kill all running tasks
    jobRun.tasks.values.filter(t => isActive(t.status)).foreach { t =>
      driverHolder.driver.foreach(_.killTask(t.id.mesosTaskId))
    }
    launchQueue.purge(runSpec.id)

    // Abort the jobRun
    // TODO: JobRunStatus.Aborted?
    jobRun = jobRun.copy(status = JobRunStatus.Failed)
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.debug("become aborting")
    context.become(aborting)
  }

  // Behavior

  override def receive: Receive = around {
    receiveKill orElse {
      case msg: Any => log.warning("Cannot handle {}; not initialized.", msg)
    }
  }

  def receiveKill: Receive = {
    case KillCurrentJobRun =>
      log.info(s"Kill execution of JobRun ${jobRun.id}")
      becomeAborting()
  }

  def creating: Receive = around {
    receiveKill orElse {
      case JobRunCreated(_, persisted, _) =>
        becomeStarting(persisted)

      case PersistFailed(_, id, ex, _) =>
        becomeAborting()
    }
  }

  def starting: Receive = around {
    receiveKill orElse {
      case ForwardStatusUpdate(update) if isActive(update) =>
        becomeActive(update)

      case ForwardStatusUpdate(update) if isFinished(update) =>
        becomeFinishing(update)

      case ForwardStatusUpdate(update) if isFailed(update) =>
        continueOrBecomeFailing(update)

      case JobRunUpdated(_, persisted, _) =>
        log.debug(s"JobRun ${persisted.id} has been persisted")

      case PersistFailed(_, id, ex, _) =>
        becomeAborting()
    }
  }

  def active: Receive = around {
    receiveKill orElse {
      case ForwardStatusUpdate(update) if isFinished(update) =>
        becomeFinishing(update)

      case ForwardStatusUpdate(update) if isFailed(update) =>
        continueOrBecomeFailing(update)

      case JobRunUpdated(_, persisted, _) => log.debug(s"JobRun ${persisted.id} has been persisted")

      case PersistFailed(_, id, ex, _)    => becomeAborting()
    }
  }

  def finishing: Receive = around {
    receiveKill orElse {
      case JobRunDeleted(_, persisted, _) =>
        log.info(s"Execution of JobRun ${jobRun.id} has been finished")
        val result = JobResult(jobRun)
        context.parent ! Finished(result)
        promise.success(result)
        context.become(terminal)

      case PersistFailed(_, id, ex, _) =>
        log.info(s"Execution of JobRun ${jobRun.id} has been finished but deleting the jobRun failed")
        jobRun = jobRun.copy(status = JobRunStatus.Failed)
        val result = JobResult(jobRun)
        context.parent ! JobRunExecutorActor.Aborted(result)
        promise.failure(JobRunFailed(result))
        context.become(terminal)
    }
  }

  def failing: Receive = around {
    receiveKill orElse {
      case JobRunDeleted(_, persisted, _) =>
        log.info(s"Execution of JobRun ${jobRun.id} has failed")
        val result = JobResult(jobRun)
        context.parent ! JobRunExecutorActor.Failed(result)
        promise.failure(JobRunFailed(result))
        context.become(terminal)

      case PersistFailed(_, id, ex, _) =>
        val result = JobResult(jobRun)
        context.parent ! JobRunExecutorActor.Aborted(result)
        promise.failure(JobRunFailed(result))
        context.become(terminal)
    }
  }

  def aborting: Receive = around {
    receiveKill orElse {
      // We can't handle a successful deletion and a failure differently
      case JobRunDeleted(_, persisted, _) =>
        log.info(s"Execution of JobRun ${jobRun.id} was aborted")
        val result = JobResult(jobRun)
        context.parent ! Aborted(result)
        promise.failure(JobRunFailed(result))
        context.become(terminal)

      case PersistFailed(_, id, ex, _) =>
        log.info(s"Execution of JobRun ${jobRun.id} was aborted and deleting failed")
        val result = JobResult(jobRun)
        context.parent ! Aborted(result)
        promise.failure(JobRunFailed(result))
        context.become(terminal)
    }
  }

  def terminal: Receive = around {
    case _ => log.debug("Actor terminal; not handling or expecting any more messages")
  }

}

object JobRunExecutorActor {

  case object KillCurrentJobRun
  case class JobRunUpdate(startedJobRun: StartedJobRun)

  case class Finished(jobResult: JobResult)
  case class Failed(jobResult: JobResult)
  case class Aborted(jobResult: JobResult)

  case class ForwardStatusUpdate(update: MesosStatusUpdateEvent)

  def props(
    run:                        JobRun,
    promise:                    Promise[JobResult],
    persistenceActorRefFactory: (JobRunId, ActorContext) => ActorRef,
    launchQueue:                LaunchQueue,
    driverHolder:               MarathonSchedulerDriverHolder,
    clock:                      Clock,
    behavior:                   Behavior
  ): Props = Props(
    new JobRunExecutorActor(run, promise, persistenceActorRefFactory, launchQueue, driverHolder, clock, behavior)
  )
}

object TaskStates {
  import mesos.Protos.TaskState._

  private[this] def asTaskState(value: String): Option[mesos.Protos.TaskState] =
    Try { mesos.Protos.TaskState.valueOf(value) }.toOption

  private[this] val active = Set(TASK_STAGING, TASK_STARTING, TASK_RUNNING)
  def isActive(taskStatus: String): Boolean = asTaskState(taskStatus).fold(false)(active)
  def isActive(event: MesosStatusUpdateEvent): Boolean = isActive(event.taskStatus)

  private[this] val failed = Set(TASK_ERROR, TASK_FAILED, TASK_LOST)
  def isFailed(event: MesosStatusUpdateEvent): Boolean = asTaskState(event.taskStatus).fold(false)(failed)

  def isFinished(event: MesosStatusUpdateEvent): Boolean =
    asTaskState(event.taskStatus).fold(false)(_ == mesos.Protos.TaskState.TASK_FINISHED)
}

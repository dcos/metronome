package dcos.metronome
package jobrun.impl

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorRef, Cancellable, Props, Stash }
import dcos.metronome.{ JobRunFailed, UnexpectedTaskState }
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model._
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.Task.LaunchedEphemeral
import mesosphere.marathon.core.task.tracker.TaskTracker
import org.joda.time.Seconds

import scala.concurrent.Promise
import scala.concurrent.duration.Duration

/**
  * Handles one job run from start until the job either completes successful or failed.
  *
  * @param run the related job run object.
  */
class JobRunExecutorActor(
    run:                        JobRun,
    startingDeadline:           Option[Duration],
    promise:                    Promise[JobResult],
    persistenceActorRefFactory: (JobRunId, ActorContext) => ActorRef,
    launchQueue:                LaunchQueue,
    taskTracker:                TaskTracker,
    driverHolder:               MarathonSchedulerDriverHolder,
    clock:                      Clock,
    val behavior:               Behavior
) extends Actor with Stash with ActorLogging with ActorBehavior {
  import JobRunExecutorActor._
  import JobRunPersistenceActor._
  import TaskStates._
  import context.dispatcher

  private[impl] var startingDeadlineTimer: Option[Cancellable] = None

  lazy val persistenceActor = persistenceActorRefFactory(run.id, context)
  var jobRun: JobRun = run

  val runSpecId = jobRun.id.toPathId

  override def preStart(): Unit = {
    scheduleStartingDeadlineTimeout()

    jobRun.status match {
      case JobRunStatus.Initial => becomeCreating()

      case JobRunStatus.Starting | JobRunStatus.Active =>
        launchQueue.get(runSpecId) match {
          case Some(info) if info.finalTaskCount > 0 =>
            log.info("found an active launch queue for {}, not scheduling more tasks", runSpecId)
            val tasks = taskTracker.appTasksLaunchedSync(runSpecId).collect {
              // FIXME: we do currently only allow non-resident tasks. Since there is no clear state on
              // Marathon's task representation, this is the safest conversion we can do for now:
              case task: LaunchedEphemeral => JobRunTask(task)
              case task: Task              => throw new UnexpectedTaskState(task)
            }
            self ! Initialized(tasks)

          case _ =>
            self ! Initialized(tasks = Nil)
        }

      case JobRunStatus.Success => becomeFinishing(jobRun)

      case JobRunStatus.Failed  => becomeFailing(jobRun)
    }
  }

  // Transitions

  def becomeCreating(): Unit = {
    log.info(s"Create JobRun ${jobRun.id} - become creating")
    persistenceActor ! Create(jobRun.copy(status = JobRunStatus.Starting))
    context.become(creating)
  }

  def becomeStarting(updatedJobRun: JobRun): Unit = {
    jobRun = updatedJobRun
    log.info(s"Execution of JobRun ${jobRun.id} has been started - become starting")
    addTaskToLaunchQueue()

    context.become(starting)
  }

  def scheduleStartingDeadlineTimeout(): Unit = {
    import scala.concurrent.duration._

    startingDeadline.foreach { deadline =>
      val now = clock.now()
      val from = run.createdAt.plus(deadline.toMillis)
      val timeout = Seconds.secondsBetween(now, from).getSeconds.seconds
      startingDeadlineTimer = Some(context.system.scheduler.scheduleOnce(timeout, self, StartTimeout))
    }
  }

  def addTaskToLaunchQueue(): Unit = {
    log.info("addTaskToLaunchQueue")
    import dcos.metronome.utils.glue.MarathonImplicits._
    launchQueue.add(jobRun.toRunSpec, count = 1)
  }

  def becomeActive(update: TaskStateChangedEvent): Unit = {
    cancelStartingDeadline()
    jobRun = jobRun.copy(status = JobRunStatus.Active, tasks = updatedTasks(update))
    persistenceActor ! Update(_ => jobRun)
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))

    log.debug("become active")
    context.become(active)
  }

  def cancelStartingDeadline(): Unit = {
    startingDeadlineTimer.foreach { c => if (!c.isCancelled) c.cancel() }
    startingDeadlineTimer = None
  }

  def updatedTasks(update: TaskStateChangedEvent): Map[Task.Id, JobRunTask] = {
    // Note: there is a certain inaccuracy when we receive a finished task that's not in the Map
    // This will be fault tolerant and still add it, but startedAt and completedAt will be the same
    // in this case because we don't know the startedAt timestamp
    def completedAt = if (update.taskState == TaskState.Finished) Some(update.timestamp) else None
    val updatedTask = jobRun.tasks.get(update.taskId).map { t =>
      t.copy(
        completedAt = completedAt,
        status = update.taskState)
    }.getOrElse {
      JobRunTask(
        id = update.taskId,
        startedAt = update.timestamp,
        completedAt = completedAt,
        status = update.taskState)
    }

    jobRun.tasks + (updatedTask.id -> updatedTask)
  }

  def becomeFinishing(updatedJobRun: JobRun): Unit = {
    launchQueue.purge(runSpecId)
    jobRun = updatedJobRun
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.debug("become finishing")
    context.become(finishing)
  }

  def continueOrBecomeFailing(update: TaskStateChangedEvent): Unit = {
    def inTime: Boolean = jobRun.jobSpec.run.restart.activeDeadline.fold(true) { deadline =>
      jobRun.createdAt.plus(deadline.toMillis).getMillis > clock.now().getMillis
    }
    jobRun.jobSpec.run.restart.policy match {
      case RestartPolicy.OnFailure if inTime =>
        log.info("still in time, launching another task")
        jobRun = jobRun.copy(tasks = updatedTasks(update))
        persistenceActor ! Update(_ => jobRun)
        context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
        addTaskToLaunchQueue()

      case _ =>
        becomeFailing(jobRun.copy(
          status = JobRunStatus.Failed,
          tasks = updatedTasks(update),
          completedAt = Some(clock.now())))
    }
  }

  // FIXME: compare to becomeFinishing, there's lots of DRY violation
  def becomeFailing(updatedJobRun: JobRun): Unit = {
    launchQueue.purge(runSpecId)
    jobRun = updatedJobRun
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.info("become failing")
    context.become(failing)
  }

  def becomeAborting(): Unit = {
    log.info(s"Execution of JobRun ${jobRun.id} has been aborted")
    // kill all running tasks
    jobRun.tasks.values.filter(t => isActive(t.status)).foreach { t =>
      driverHolder.driver.foreach(_.killTask(t.id.mesosTaskId))
    }
    launchQueue.purge(runSpecId)

    // Abort the jobRun
    jobRun = jobRun.copy(
      status = JobRunStatus.Failed,
      completedAt = Some(clock.now()))
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.debug("become aborting")
    context.become(aborting)
  }

  // Behavior

  override def receive: Receive = around {
    case Initialized(Nil) =>
      log.info("initializing - no existing tasks in the queue")
      becomeStarting(jobRun)
      unstashAll()

    case Initialized(tasks) =>
      log.info("initializing - found existing tasks in the queue")
      // sync the state with loaded tasks
      // since the task tracker only stores active tasks, we don't remove anything here
      tasks.foreach { task =>
        jobRun.tasks + (task.id -> task)
      }
      if (tasks.exists(t => isActive(t.status))) {
        // the actor is already active, so don't transition to active, just switch
        context.become(active)
      } else {
        becomeStarting(jobRun)
      }
      unstashAll()

    case _ => stash()
  }

  def receiveStartTimeout: Receive = {
    case StartTimeout =>
      log.info(s"Start timed out for JobRun ${jobRun.id} with deadline ${startingDeadline.get}")
      becomeAborting()
  }

  def receiveKill: Receive = {
    case KillCurrentJobRun =>
      log.info(s"Kill execution of JobRun ${jobRun.id}")
      becomeAborting()
  }

  def creating: Receive = around {
    receiveKill orElse receiveStartTimeout orElse {
      case JobRunCreated(_, updatedJobRun, _) =>
        becomeStarting(updatedJobRun)

      case PersistFailed(_, id, ex, _) =>
        becomeAborting()
    }
  }

  def starting: Receive = around {
    receiveKill orElse receiveStartTimeout orElse {
      case ForwardStatusUpdate(update) if isActive(update.taskState) =>
        becomeActive(update)

      case ForwardStatusUpdate(update) if isFinished(update.taskState) =>
        becomeFinishing(jobRun.copy(
          status = JobRunStatus.Success,
          tasks = updatedTasks(update),
          completedAt = Some(update.timestamp)))

      case ForwardStatusUpdate(update) if isFailed(update.taskState) =>
        continueOrBecomeFailing(update)

      case JobRunUpdated(_, persisted, _) =>
        log.debug(s"JobRun ${persisted.id} has been persisted")

      case PersistFailed(_, id, ex, _) =>
        becomeAborting()
    }
  }

  def active: Receive = around {
    receiveKill orElse receiveStartTimeout orElse {
      case ForwardStatusUpdate(update) if isFinished(update.taskState) =>
        becomeFinishing(jobRun.copy(
          status = JobRunStatus.Success,
          tasks = updatedTasks(update),
          completedAt = Some(update.timestamp)))

      case ForwardStatusUpdate(update) if isFailed(update.taskState) =>
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
        jobRun = jobRun.copy(
          status = JobRunStatus.Failed,
          completedAt = Some(clock.now()))
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

  def terminal: Receive = around {
    case _ => log.debug("Actor terminal; not handling or expecting any more messages")
  }

}

object JobRunExecutorActor {

  case class Initialized(tasks: Iterable[JobRunTask])

  case object KillCurrentJobRun
  case class JobRunUpdate(startedJobRun: StartedJobRun)

  case class Finished(jobResult: JobResult)
  case class Failed(jobResult: JobResult)
  case class Aborted(jobResult: JobResult)

  case object StartTimeout

  case class ForwardStatusUpdate(update: TaskStateChangedEvent)

  def props(
    run:                        JobRun,
    startingDeadline:           Option[Duration],
    promise:                    Promise[JobResult],
    persistenceActorRefFactory: (JobRunId, ActorContext) => ActorRef,
    launchQueue:                LaunchQueue,
    taskTracker:                TaskTracker,
    driverHolder:               MarathonSchedulerDriverHolder,
    clock:                      Clock,
<<<<<<< HEAD
    behavior:                   Behavior): Props = Props(
    new JobRunExecutorActor(run, promise, persistenceActorRefFactory,
      launchQueue, taskTracker, driverHolder, clock, behavior))
=======
    behavior:                   Behavior
  ): Props = Props(
    new JobRunExecutorActor(run, startingDeadline, promise, persistenceActorRefFactory,
      launchQueue, taskTracker, driverHolder, clock, behavior)
  )
>>>>>>> Implement start deadline METRONOME-191
}

object TaskStates {
  import dcos.metronome.scheduler.TaskState

  private[this] val active = Set[TaskState](TaskState.Staging, TaskState.Starting, TaskState.Running)
  def isActive(taskState: TaskState): Boolean = active(taskState)
  private[this] val failed = Set[TaskState](TaskState.Failed, TaskState.Killed)
  def isFailed(taskState: TaskState): Boolean = failed(taskState)
  def isFinished(taskState: TaskState): Boolean = taskState == TaskState.Finished
}

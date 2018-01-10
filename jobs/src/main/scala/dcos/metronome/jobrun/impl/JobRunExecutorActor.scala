package dcos.metronome
package jobrun.impl

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorRef, Cancellable, Props, Scheduler, Stash }
import dcos.metronome.{ JobRunFailed, UnexpectedTaskState }
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus, JobRunTask, RestartPolicy }
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.Task.LaunchedEphemeral
import mesosphere.marathon.core.task.tracker.TaskTracker
import org.joda.time.Seconds

import scala.concurrent.Promise
import scala.concurrent.duration.{ Duration, FiniteDuration }

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
  taskTracker:                TaskTracker,
  driverHolder:               MarathonSchedulerDriverHolder,
  clock:                      Clock,
  val behavior:               Behavior)(implicit scheduler: Scheduler) extends Actor with Stash with ActorLogging with ActorBehavior {
  import JobRunExecutorActor._
  import JobRunPersistenceActor._
  import TaskStates._
  import context.dispatcher

  private[impl] var startingDeadlineTimer: Option[Cancellable] = None

  lazy val persistenceActor = persistenceActorRefFactory(run.id, context)
  var jobRun: JobRun = run

  val runSpecId = jobRun.id.toPathId

  override def preStart(): Unit = {
    // only 2 ways we should be here... 1) newly launched jobrun and 2) startup while last known jobruns were launched
    log.info(s"Starting jobrun ${jobRun} with id ${jobRun.id} status: ${jobRun.status}")

    jobRun.status match {
      // on new jobrun
      case JobRunStatus.Initial => becomeCreating()

      // previously launched job (from zk) only possible with restart
      case JobRunStatus.Starting =>
        println(s"Initializing Actor for existing jobrun ${jobRun} in Starting state not in the queue.")
        context.become(creating)
      case JobRunStatus.Active =>
        // task not in queue but already launched
        println(s"Initializing Actor for existing jobrun ${jobRun} in Active state not in the queue.")
        self ! Initialized()

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
    // startdeadline timeout is for anything going to the launchqueue to start
    val startingDeadline = computeStartingDeadline
    if (startingDeadline.exists(d => d.toSeconds <= 0)) {
      log.info(s"StartingDeadline timeout of ${jobRun.startingDeadline.get} expired for JobRun ${jobRun.id} created at ${jobRun.createdAt}")
      becomeAborting()
    } else {
      startingDeadline.foreach(scheduleStartingDeadline)
      addTaskToLaunchQueue()
      context.become(starting)
    }
  }

  def computeStartingDeadline: Option[FiniteDuration] = {
    import scala.concurrent.duration._

    jobRun.startingDeadline.map { deadline =>
      val now = clock.now()
      val from = run.createdAt.plus(deadline.toMillis)
      Seconds.secondsBetween(now, from).getSeconds.seconds
    }
  }

  def scheduleStartingDeadline(timeout: FiniteDuration): Unit = {
    log.info(s"Timeout scheduled for ${jobRun.id} at $timeout")
    startingDeadlineTimer = Some(scheduler.scheduleOnce(timeout, self, StartTimeout))
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

    // this is called because the check is in the launch queue which on restart is cleared
    // if recorded in zk we need to assume it is running... a reconc will indicate that it isn't any longer
    case Initialized() =>
      log.info("initializing - no existing tasks in the queue")
      context.become(active)
      unstashAll()

    case _ => stash()
  }

  def receiveStartTimeout: Receive = {
    case StartTimeout =>
      log.info(s"StartingDeadline timeout of ${jobRun.startingDeadline.get} expired for JobRun ${jobRun.id} created at ${jobRun.createdAt}")
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
    receiveKill orElse {
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

  case class Initialized()

  case object KillCurrentJobRun
  case class JobRunUpdate(startedJobRun: StartedJobRun)

  case class Finished(jobResult: JobResult)
  case class Failed(jobResult: JobResult)
  case class Aborted(jobResult: JobResult)

  case object StartTimeout

  case class ForwardStatusUpdate(update: TaskStateChangedEvent)

  def props(
    run:                        JobRun,
    promise:                    Promise[JobResult],
    persistenceActorRefFactory: (JobRunId, ActorContext) => ActorRef,
    launchQueue:                LaunchQueue,
    taskTracker:                TaskTracker,
    driverHolder:               MarathonSchedulerDriverHolder,
    clock:                      Clock,
    behavior:                   Behavior)(implicit scheduler: Scheduler): Props = Props(
    new JobRunExecutorActor(run, promise, persistenceActorRefFactory,
      launchQueue, taskTracker, driverHolder, clock, behavior))
}

object TaskStates {
  import dcos.metronome.scheduler.TaskState

  private[this] val active = Set[TaskState](TaskState.Staging, TaskState.Starting, TaskState.Running)
  def isActive(taskState: TaskState): Boolean = active(taskState)
  private[this] val failed = Set[TaskState](TaskState.Failed, TaskState.Killed)
  def isFailed(taskState: TaskState): Boolean = failed(taskState)
  def isFinished(taskState: TaskState): Boolean = taskState == TaskState.Finished
}

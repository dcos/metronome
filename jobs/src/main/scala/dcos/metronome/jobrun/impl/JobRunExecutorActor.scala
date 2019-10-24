package dcos.metronome
package jobrun.impl

import java.time.Clock
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorRef, Cancellable, Props, Scheduler, Stash }
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus, JobRunTask, RestartPolicy }
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.glue.MarathonImplicits
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.tracker.InstanceTracker
import mesosphere.marathon.{ AllConf, MarathonSchedulerDriverHolder, StoreCommandFailedException }
import org.apache.zookeeper.KeeperException.NodeExistsException

import scala.async.Async.async
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

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
  instanceTracker:            InstanceTracker,
  driverHolder:               MarathonSchedulerDriverHolder,
  config:                     AllConf,
  clock:                      Clock)(implicit scheduler: Scheduler) extends Actor with Stash with ActorLogging {
  import JobRunExecutorActor._
  import JobRunPersistenceActor._
  import TaskStates._
  import context.dispatcher

  private[impl] var startingDeadlineTimer: Option[Cancellable] = None

  lazy val persistenceActor = persistenceActorRefFactory(run.id, context)
  var jobRun: JobRun = run

  val runSpecId = jobRun.id.toPathId

  override def preStart(): Unit = {
    val startingDeadline = computeStartingDeadline
    startingDeadline.foreach(scheduleStartingDeadline)

    jobRun.status match {
      case JobRunStatus.Initial | JobRunStatus.Starting if startingDeadline.exists(d => d.toSeconds <= 0) =>
        log.info(s"StartingDeadline timeout of ${jobRun.startingDeadline.get} expired for JobRun ${jobRun.id} created at ${jobRun.createdAt}")
        becomeAborting()
      case JobRunStatus.Initial =>
        becomeCreating()
      case JobRunStatus.Starting | JobRunStatus.Active =>
        val existingTasks = tasksFromInstanceTracker()
        existingTasks match {
          case tasks if tasks.nonEmpty =>
            // the actor was probably just restarted and we did not lose contents of the launch queue
            self ! Initialized(tasks)
          case _ if jobRun.status == JobRunStatus.Starting && existsInLaunchQueue() =>
            // we did attempt to launch something but there is no known existing task yet
            // this is exactly the same as starting state with already launched item
            context.become(starting)
          case _ =>
            self ! Initialized(Nil)
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

  def computeStartingDeadline: Option[FiniteDuration] = {
    import scala.concurrent.duration._

    jobRun.startingDeadline.map { deadline =>
      val now = clock.instant()
      val from = run.createdAt.plusMillis(deadline.toMillis)
      Duration.apply(from.toEpochMilli - now.toEpochMilli, TimeUnit.MILLISECONDS)
    }
  }

  def scheduleStartingDeadline(timeout: FiniteDuration): Unit = {
    log.info(s"Timeout scheduled for ${jobRun.id} at $timeout")
    startingDeadlineTimer = Some(scheduler.scheduleOnce(timeout, self, StartTimeout))
  }

  def addTaskToLaunchQueue(restart: Boolean = false): Unit = async {
    if (!restart && existsInLaunchQueue()) {
      // we have to handle a case when actor is restarted (e.g. because of exception) and it already put something into the queue
      // during restart it is possible, that actor that was in state Starting will be restarted with state initial
      log.info(s"Job run ${jobRun.id} already exists in LaunchQueue - not adding")
    } else {
      log.info("addTaskToLaunchQueue")
      val runSpec = MarathonImplicits.toRunSpec(jobRun, config.mesosRole())
      launchQueue.add(runSpec, count = 1)
    }
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

  def tasksFromInstanceTracker(): Iterable[JobRunTask] = {
    instanceTracker.specInstancesSync(runSpecId).map(a => a.appTask).collect {
      case task: Task => JobRunTask(task)
      case task       => throw UnexpectedTaskState(task)
    }
  }

  def existsInLaunchQueue(): Boolean = {
    instanceTracker.instancesBySpecSync.specInstances(runSpecId).nonEmpty
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
    jobRun = updatedJobRun
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.debug("become finishing")
    context.become(finishing)
  }

  def continueOrBecomeFailing(update: TaskStateChangedEvent): Unit = {
    def inTime: Boolean = jobRun.jobSpec.run.restart.activeDeadline.fold(true) { deadline =>
      jobRun.createdAt.plusMillis(deadline.toMillis).isAfter(clock.instant())
    }
    jobRun.jobSpec.run.restart.policy match {
      case RestartPolicy.OnFailure if inTime =>
        log.info("still in time, launching another task")
        jobRun = jobRun.copy(tasks = updatedTasks(update))
        persistenceActor ! Update(_ => jobRun)
        context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
        addTaskToLaunchQueue(true)

      case _ =>
        becomeFailing(jobRun.copy(
          status = JobRunStatus.Failed,
          tasks = updatedTasks(update),
          completedAt = Some(clock.instant())))
    }
  }

  // FIXME: compare to becomeFinishing, there's lots of DRY violation
  def becomeFailing(updatedJobRun: JobRun): Unit = {
    jobRun = updatedJobRun
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.info("become failing")
    context.become(failing)
  }

  def becomeAborting(): Unit = {
    cancelStartingDeadline()
    log.info(s"Execution of JobRun ${jobRun.id} has been aborted")
    // kill all running tasks
    jobRun.tasks.values.filter(t => isActive(t.status)).foreach { t =>
      driverHolder.driver.foreach(_.killTask(t.id.mesosTaskId))
    }

    // Abort the jobRun
    jobRun = jobRun.copy(
      status = JobRunStatus.Failed,
      completedAt = Some(clock.instant()))
    context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
    persistenceActor ! Delete(jobRun)

    log.debug("become aborting")
    context.become(aborting)
  }

  // Behavior

  override def receive: Receive = {
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
      log.info(s"StartingDeadline timeout of ${jobRun.startingDeadline.get} expired for JobRun ${jobRun.id} created at ${jobRun.createdAt}")
      becomeAborting()
  }

  def receiveKill: Receive = {
    case KillCurrentJobRun =>
      log.info(s"Kill execution of JobRun ${jobRun.id}")
      becomeAborting()
  }

  def creating: Receive = {
    receiveKill orElse receiveStartTimeout orElse {
      case JobRunCreated(_, updatedJobRun, _) =>
        becomeStarting(updatedJobRun)

      case PersistFailed(_, _, ex, _) if ex.isInstanceOf[StoreCommandFailedException] && ex.getCause.isInstanceOf[NodeExistsException] =>
        // we need to be able to handle restarted actor that already created the ZK node in the previous run
        becomeStarting(jobRun.copy(status = JobRunStatus.Starting))

      case PersistFailed(_, id, ex, _) =>
        becomeAborting()
    }
  }

  def starting: Receive = {
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

  def active: Receive = {
    receiveKill orElse {
      case ForwardStatusUpdate(update) if isActive(update.taskState) =>
        jobRun = jobRun.copy(status = JobRunStatus.Active, tasks = updatedTasks(update))
        persistenceActor ! Update(_ => jobRun)
        context.parent ! JobRunUpdate(StartedJobRun(jobRun, promise.future))
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

  def finishing: Receive = {
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
          completedAt = Some(clock.instant()))
        val result = JobResult(jobRun)
        context.parent ! JobRunExecutorActor.Aborted(result)
        promise.failure(JobRunFailed(result))
        context.become(terminal)
    }
  }

  def failing: Receive = {
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

  def aborting: Receive = {
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

  def terminal: Receive = {
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
    promise:                    Promise[JobResult],
    persistenceActorRefFactory: (JobRunId, ActorContext) => ActorRef,
    launchQueue:                LaunchQueue,
    instanceTracker:            InstanceTracker,
    driverHolder:               MarathonSchedulerDriverHolder,
    config:                     AllConf,
    clock:                      Clock)(implicit scheduler: Scheduler): Props = Props(
    new JobRunExecutorActor(run, promise, persistenceActorRefFactory,
      launchQueue, instanceTracker, driverHolder, config, clock))
}

object TaskStates {
  import dcos.metronome.scheduler.TaskState

  private[this] val active = Set[TaskState](TaskState.Staging, TaskState.Starting, TaskState.Running)
  def isActive(taskState: TaskState): Boolean = active(taskState)
  private[this] val failed = Set[TaskState](TaskState.Failed, TaskState.Killed)
  def isFailed(taskState: TaskState): Boolean = failed(taskState)
  def isFinished(taskState: TaskState): Boolean = taskState == TaskState.Finished
}

package dcos.metronome
package scheduler.impl

import akka.actor.{ FSM, Props }
import dcos.metronome.model.Event.ReconciliationFinished
import dcos.metronome.scheduler.SchedulerConfig
import dcos.metronome.scheduler.impl.ReconciliationActor._
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.tracker.InstanceTracker

import scala.util.control.NonFatal

class ReconciliationActor(
  driverHolder:    MarathonSchedulerDriverHolder,
  instanceTracker: InstanceTracker,
  config:          SchedulerConfig) extends FSM[State, Data] {

  startWith(init(), NoData)

  when(Idle, stateTimeout = config.reconciliationInterval) {
    case Event(StateTimeout, _) =>
      goto(Loading) using NoData
  }

  when(Loading) {
    case Event(TasksLoaded(tasks), _) =>
      log.debug("received tasks from instanceTracker")
      goto(Reconciling) using ReconciliationData(tasks)

    case Event(TaskLoadingFailed(error), _) =>
      log.warning("Loading tasks failed: {}", error)
      goto(Reconciling) using ReconciliationData(Iterable.empty)
  }

  when(Reconciling, stateTimeout = config.reconciliationTimeout) {
    // TODO: implement a proper reconciliation and block certain other actors until it is considered finished
    // Background: there's no guarantee we'll receive status updates for each task
    // if mesos agents or masters fail over, so we need to subsequently trigger explicit reconciliation
    // for tasks that haven't been reported.
    // see http://mesos.apache.org/documentation/latest/reconciliation/
    // In order to do that we'd need to handle status update events - e.g. via the event bus
    case Event(StateTimeout, _) =>
      log.info("state timeout while reconciling. Considering as finished.")
      goto(Idle) using NoData

    case _ => stay()
  }

  whenUnhandled {
    case Event(event, state) =>
      log.debug(s"Unhandled event $event in $state")
      stay()
  }

  onTransition {
    case _ -> Idle =>
      context.system.eventStream.publish(ReconciliationFinished())
      log.debug("Entered Idle state")

    case Idle -> Loading =>
      log.info("Entered Loading state")
      loadTasks()

    case Loading -> Reconciling =>
      nextStateData match {
        case ReconciliationData(tasks) =>
          if (tasks.nonEmpty) reconcileExplicitly(tasks)
          reconcileImplicitly()

        case _ =>
          log.error(s"unexpected state data in $stateName: $nextStateData")
      }
  }

  initialize()

  // helper functions

  private[this] def init(): ReconciliationActor.State = {
    loadTasks()

    // return the initial state
    Loading
  }

  private[this] def loadTasks(): Unit = {
    import context.dispatcher
    instanceTracker.instancesBySpec().map { instancesBySpec =>
      // we know that metronome job always has only one task
      self ! TasksLoaded(instancesBySpec.allInstances.map(i => i.appTask))
    } recover {
      case NonFatal(error) => self ! TaskLoadingFailed(error)
    }
  }

  private[this] def reconcileExplicitly(tasks: Iterable[Task]): Unit = {
    driverHolder.driver.foreach { driver =>
      log.info("Performing explicit reconciliation for {} tasks", tasks.size)
      import scala.collection.JavaConverters._
      val statuses = tasks.flatMap(_.status.mesosStatus).asJavaCollection
      driver.reconcileTasks(statuses)
    }
  }

  private[this] def reconcileImplicitly(): Unit = driverHolder.driver.foreach {
    log.info("Performing implicit reconciliation")
    _.reconcileTasks(java.util.Arrays.asList())
  }

}

object ReconciliationActor {

  case class TasksLoaded(tasks: Iterable[Task])
  case class TaskLoadingFailed(error: Throwable)

  sealed trait State
  case object Idle extends State
  case object Loading extends State
  case object Reconciling extends State

  sealed trait Data
  case object NoData extends Data
  case class ReconciliationData(tasks: Iterable[Task]) extends Data

  def props(
    driverHolder:    MarathonSchedulerDriverHolder,
    instanceTracker: InstanceTracker,
    config:          SchedulerConfig): Props =
    Props(new ReconciliationActor(driverHolder, instanceTracker, config))
}

package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.{ JobSpec, ScheduleSpec }
import dcos.metronome.utils.time.Clock
import org.joda.time.{ DateTime, Seconds }

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

/**
  * Manages one JobSpec.
  * If the JobSpec has a schedule, the schedule is triggered in this actor.
  */
class JobSpecSchedulerActor(
    initSpec:     JobSpec,
    clock:        Clock,
    runService:   JobRunService,
    val behavior: Behavior
) extends Actor with Stash with ActorLogging with ActorBehavior {

  import JobSpecSchedulerActor._
  import context.dispatcher

  private[impl] var spec = initSpec
  private[impl] var schedules = TrieMap.empty[ScheduleSpec, ScheduleActions]

  override def preStart(): Unit = {
    spec.schedules.foreach(scheduleNextRun(_))
  }

  override def postStop(): Unit = {
    spec.schedules.foreach(cancelSchedule(_))
  }

  override def receive: Receive = around {
    case StartJob(s)            => runJob(s)
    case UpdateJobSpec(newSpec) => updateSpec(newSpec)
  }

  def updateSpec(newSpec: JobSpec): Unit = {
    log.info(s"JobSpec ${newSpec.id} has been updated. Reschedule.")

    spec = newSpec

    // Identify deleted, new and changed schedules
    val deletedSchedules = schedules.keys.filterNot(newSpec.schedules.contains(_)).toSet
    val newSchedules = newSpec.schedules.filterNot(schedules.keys.toSet)
    val updateableSchedules = schedules.keys.filter(newSpec.schedules.contains(_)).toSet

    // Cancel all removed schedules
    deletedSchedules.foreach(cancelSchedule(_))

    // Initialize all new schedules
    newSchedules.foreach(scheduleNextRun(_))

    // Update existing schedules if necessary
    updateableSchedules.foreach { updateableSchedule =>
      val update = spec.schedules.find(_.id == updateableSchedule.id).fold(true) { newSchedule =>
        updateableSchedule != newSchedule
      }

      if (update) {
        spec.schedules.find(_.id == updateableSchedule.id) foreach { newSchedule =>
          cancelSchedule(updateableSchedule)
          scheduleNextRun(newSchedule)
        }
      }
    }
  }

  def runJob(s: ScheduleSpec): Unit = {
    log.info(s"Start next run of schedule ${s.id} of job ${spec.id}, which was scheduled for ${schedules.get(s).map(_.scheduledAt)}")
    runService.startJobRun(spec)
    scheduleNextRun(s)
  }

  def scheduleNextRun(s: ScheduleSpec): Unit = {
    val scheduleActionsOpt = schedules.get(s)
    val c = scheduleActionsOpt.map(_.cancellable)
    val scheduledAt = scheduleActionsOpt.map(_.scheduledAt)

    cancelSchedule(s)

    val now = clock.now()
    val lastScheduledAt = scheduledAt
    val from = lastScheduledAt.getOrElse(now)

    val nextTime = s.nextExecution(from)
    val in = Seconds.secondsBetween(now, nextTime).getSeconds.seconds

    schedules += s -> ScheduleActions(context.system.scheduler.scheduleOnce(in, self, StartJob), nextTime)

    log.info(s"Spec ${spec.id}: next run of scheduler ${s.id} is scheduled for: $nextTime (in $in)")
  }

  def cancelSchedule(s: ScheduleSpec): Unit = {
    schedules.get(s) foreach { case ScheduleActions(c, _) => if (!c.isCancelled) c.cancel() }
    schedules -= s
  }
}

object JobSpecSchedulerActor {

  case class StartJob(s: ScheduleSpec)
  case class UpdateJobSpec(newSpec: JobSpec)

  def props(spec: JobSpec, clock: Clock, runService: JobRunService, behavior: Behavior): Props = {
    Props(new JobSpecSchedulerActor(spec, clock, runService, behavior))
  }
}

final case class ScheduleActions(cancellable: Cancellable, scheduledAt: DateTime)

package dcos.metronome
package jobspec.impl

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit

import akka.actor._
import dcos.metronome.behavior.{ActorBehavior, Behavior}
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.{JobSpec, ScheduleSpec}

import scala.concurrent.duration._

/**
  * Manages one JobSpec.
  * If the JobSpec has a schedule, the schedule is triggered in this actor.
  */
class JobSpecSchedulerActor(
  initSpec:     JobSpec,
  clock:        Clock,
  runService:   JobRunService,
  val behavior: Behavior) extends Actor with Stash with ActorLogging with ActorBehavior {

  import JobSpecSchedulerActor._
  import context.dispatcher

  private[impl] var spec = initSpec
  private[impl] var nextSchedule: Option[Cancellable] = None
  private[impl] var scheduledAt: Option[Instant] = None

  override def preStart(): Unit = {
    scheduleNextRun()
  }

  override def postStop(): Unit = {
    cancelSchedule()
  }

  override def receive: Receive = around {
    case StartJob(schedule)     => runJob(schedule)
    case UpdateJobSpec(newSpec) => updateSpec(newSpec)
  }

  def updateSpec(newSpec: JobSpec): Unit = {
    log.info(s"JobSpec ${newSpec.id} has been updated. Reschedule.")
    spec = newSpec
    scheduledAt = None
    scheduleNextRun()
  }

  def runJob(schedule: ScheduleSpec): Unit = {
    log.info(s"Start next run of job ${spec.id}, which was scheduled for $scheduledAt")
    runService.startJobRun(spec, Some(schedule))
    scheduleNextRun()
  }

  def scheduleNextRun(): Unit = {
    val lastScheduledAt = scheduledAt
    cancelSchedule()
    // TODO: only reschedule for one specific schedule!
    spec.schedules.foreach { schedule =>
      val now = clock.instant()
      val from = lastScheduledAt.getOrElse(now)
      val nextTime = schedule.nextExecution(from)
      scheduledAt = Some(nextTime)
      // 60 secs is the smallest unit of reschedule time for cron
      val inSeconds = Math.max(java.time.Duration.between(now, nextTime).getSeconds, 60)
      nextSchedule = Some(context.system.scheduler.scheduleOnce(Duration(inSeconds, TimeUnit.SECONDS), self, StartJob(schedule)))
      log.info(s"Spec ${spec.id}: next run is scheduled for: $nextTime (in $inSeconds seconds)")
    }
  }

  def cancelSchedule(): Unit = {
    nextSchedule.foreach { c => if (!c.isCancelled) c.cancel() }
    nextSchedule = None
    scheduledAt = None
  }
}

object JobSpecSchedulerActor {

  case class StartJob(schedule: ScheduleSpec)
  case class UpdateJobSpec(newSpec: JobSpec)

  def props(spec: JobSpec, clock: Clock, runService: JobRunService, behavior: Behavior): Props = {
    Props(new JobSpecSchedulerActor(spec, clock, runService, behavior))
  }
}

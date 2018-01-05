package dcos.metronome
package jobspec.impl

import akka.actor._
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.JobSpec
import dcos.metronome.utils.time.Clock
import org.joda.time.{ DateTime, Seconds }

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
  private[impl] var nextSchedule: Option[Cancellable] = None
  private[impl] var scheduledAt: Option[DateTime] = None

  override def preStart(): Unit = {
    scheduleNextRun()
  }

  override def postStop(): Unit = {
    cancelSchedule()
  }

  override def receive: Receive = around {
    case StartJob               => runJob()
    case UpdateJobSpec(newSpec) => updateSpec(newSpec)
  }

  def updateSpec(newSpec: JobSpec): Unit = {
    log.info(s"JobSpec ${newSpec.id} has been updated. Reschedule.")
    spec = newSpec
    scheduledAt = None
    scheduleNextRun()
  }

  def runJob(): Unit = {
    log.info(s"Start next run of job ${spec.id}, which was scheduled for $scheduledAt")
    runService.startJobRun(spec)
    scheduleNextRun()
  }

  def scheduleNextRun(): Unit = {
    val lastScheduledAt = scheduledAt
    cancelSchedule()
    // TODO: only reschedule for one specific schedule!
    spec.schedules.foreach { schedule =>
      val now = clock.now()
      val from = lastScheduledAt.getOrElse(now)
      val nextTime = schedule.nextExecution(from)
      scheduledAt = Some(nextTime)
      // 60 secs is the smallest unit of reschedule time for cron
      val in = Seconds.secondsBetween(now, nextTime).getSeconds.seconds.max(60.seconds)
      nextSchedule = Some(context.system.scheduler.scheduleOnce(in, self, StartJob))
      log.info(s"Spec ${spec.id}: next run is scheduled for: $nextTime (in $in)")
    }
  }

  def cancelSchedule(): Unit = {
    nextSchedule.foreach { c => if (!c.isCancelled) c.cancel() }
    nextSchedule = None
    scheduledAt = None
  }
}

object JobSpecSchedulerActor {

  case object StartJob
  case class UpdateJobSpec(newSpec: JobSpec)

  def props(spec: JobSpec, clock: Clock, runService: JobRunService, behavior: Behavior): Props = {
    Props(new JobSpecSchedulerActor(spec, clock, runService, behavior))
  }
}

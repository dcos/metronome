package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.model.JobSpec
import dcos.metronome.utils.time.Clock
import org.joda.time.{ DateTime, Seconds }

import scala.concurrent.duration._

/**
  * Manages one JobSpec.
  * If the JobSpec has a schedule, the schedule is triggered in this actor.
  */
class JobSpecSchedulerActor(
    initSpec: JobSpec,
    clock:    Clock
) extends Actor with Stash with ActorLogging {

  import JobSpecSchedulerActor._
  import context.dispatcher

  private[this] var spec = initSpec
  private[this] var nextSchedule: Option[Cancellable] = None
  private[this] var scheduledAt: Option[DateTime] = None

  override def preStart(): Unit = {
    scheduleNextRun()
  }

  override def postStop(): Unit = {
    cancelSchedule()
  }

  override def receive: Receive = {
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
    //TODO
    scheduleNextRun()
  }

  def scheduleNextRun(): Unit = {
    val lastScheduledAt = scheduledAt
    cancelSchedule()
    spec.schedule.foreach { schedule =>
      val now = clock.now()
      val from = lastScheduledAt.getOrElse(now)
      val nextTime = schedule.nextExecution(from)
      scheduledAt = Some(nextTime)
      val in = Seconds.secondsBetween(now, nextTime).getSeconds.seconds
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

  def props(spec: JobSpec, clock: Clock): Props = {
    Props(new JobSpecSchedulerActor(spec, clock))
  }
}

package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTime, Period }

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

/**
 * Manages one JobSpec.
 * If the JobSpec has a schedule, the schedule is triggered in this actor.
 */
class JobSpecSchedulerActor(
    initSpec:       JobSpec,
    persistPromise: Option[Promise[JobSpec]],
    repo:           JobSpecRepository
) extends Actor with Stash with ActorLogging {

  import JobSpecSchedulerActor._
  import JobSpecPersistenceActor._
  import context.dispatcher

  private[this] var persistence: ActorRef = _
  private[this] var spec = initSpec
  private[this] var nextSchedule: Option[Cancellable] = None
  private[this] var next: Option[DateTime] = None

  override def preStart(): Unit = {
    persistence = context.actorOf(JobSpecPersistenceActor.props(repo))
    persistPromise.foreach { promise =>
      persistence ! Create(initSpec, promise)
      context.become(waitForCreated)
    }
    scheduleNextRun()
  }

  override def postStop(): Unit = {
    cancelSchedule()
  }

  override def receive: Receive = {
    case StartJob                           => runJob()
    case UpdateJobSpec(id, change, promise) => persistence ! Update(id, change(spec), promise)
    case DeleteJobSpec(id, promise)         => persistence ! Delete(id, spec, promise)
    case JobSpecPersisted(newSpec)          => spec = newSpec
    case JobSpecPersistFailed               => log.warning("Persist failed!")
  }

  def waitForCreated: Receive = {
    case JobSpecPersisted(newSpec) =>
      spec = newSpec
      context.become(receive)
    case JobSpecPersistFailed =>
      log.warning("Persist failed!")
      context.stop(self)
  }

  def runJob(): Unit = {
    log.info(s"Start next run of job ${spec.id}, which was scheduled for $next")
    //TODO
    scheduleNextRun()
  }

  def scheduleNextRun(): Unit = {
    cancelSchedule()
    spec.schedule.foreach { schedule =>
      val nextTime = schedule.nextExecution()
      next = Some(nextTime)
      val in = new Period(DateTime.now, nextTime).getMillis.millis
      nextSchedule = Some(context.system.scheduler.scheduleOnce(in, self, StartJob))
      log.info(s"Spec ${spec.id}: next run is scheduled for: $nextTime (in $in)")
    }
  }

  def cancelSchedule(): Unit = {
    nextSchedule.foreach { c => if (!c.isCancelled) c.cancel() }
    nextSchedule = None
    next = None
  }
}

object JobSpecSchedulerActor {

  case object StartJob

  case class UpdateJobSpec(id: PathId, change: JobSpec => JobSpec, promise: Promise[JobSpec])
  case class DeleteJobSpec(id: PathId, promise: Promise[JobSpec])

  def props(
    spec:           JobSpec,
    persistPromise: Option[Promise[JobSpec]],
    repo:           JobSpecRepository
  ): Props = {
    Props(new JobSpecSchedulerActor(spec, persistPromise, repo))
  }
}

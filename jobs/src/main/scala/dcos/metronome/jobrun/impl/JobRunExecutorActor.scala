package dcos.metronome.jobrun.impl

import akka.actor.{ ActorLogging, Props, Actor }
import dcos.metronome.JobRunFailed
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobRunStatus, JobRunId, JobResult, JobRun }
import dcos.metronome.repository.Repository

import scala.concurrent.Promise
import scala.concurrent.duration._

/**
  * Handles one job run from start until the job either completes successful or failed.
  *
  * @param run the related job run  object.
  */
class JobRunExecutorActor(run: JobRun, promise: Promise[JobResult], repository: Repository[JobRunId, JobRun]) extends Actor with ActorLogging {
  import JobRunPersistenceActor._
  import JobRunExecutorActor._

  lazy val persistenceActor = context.actorOf(JobRunPersistenceActor.props(run.id, repository))
  var jobRun: JobRun = run

  override def preStart(): Unit = {
    jobRun.status match {
      case JobRunStatus.Starting => createJob()
      case JobRunStatus.Active   => reconcileRunningJob()
      case _                     => killCurrentRun()
    }
  }

  override def receive: Receive = {
    case KillCurrentJobRun              => killCurrentRun()
    case "job started"                  => updateJob(jobRun.copy(status = JobRunStatus.Active))
    case "job finished"                 => persistenceActor ! Delete(jobRun)

    case JobRunCreated(_, persisted, _) => startJob(persisted)
    case JobRunUpdated(_, persisted, _) => log.debug(s"JobRun ${persisted.id} has been persisted")
    case JobRunDeleted(_, persisted, _) => jobRunFinished()
    case PersistFailed(_, id, ex, _)    => jobRunAborted()
  }

  def createJob(): Unit = {
    log.info(s"Create JobRun ${jobRun.id}")
    persistenceActor ! Create(jobRun)
  }

  def startJob(run: JobRun): Unit = {
    jobRun = run
    log.info(s"Execution of JobRun ${jobRun.id} has been started")

    // TODO: fill starting logic here
    import context.dispatcher
    context.system.scheduler.scheduleOnce(15.seconds, self, "job started")
    context.system.scheduler.scheduleOnce(90.seconds, self, "job finished")
  }

  def updateJob(changed: JobRun): Unit = {
    jobRun = changed
    persistenceActor ! Update(_ => changed)
    context.parent ! JobRunUpdate(StartedJobRun(changed, promise.future))
  }

  def jobRunFinished() = {
    log.info(s"Execution of JobRun ${jobRun.id} has been finished")
    val result = JobResult(jobRun)
    context.parent ! JobRunFinished(result)
    promise.success(result)
  }

  def jobRunAborted() = {
    log.info(s"Execution of JobRun ${jobRun.id} has been aborted")
    val result = JobResult(jobRun)
    context.parent ! JobRunAborted(result)
    promise.failure(JobRunFailed(result))
  }

  def reconcileRunningJob(): Unit = {
    //TODO: fill reconciling logic here
  }

  def killCurrentRun(): Unit = {
    log.info(s"Kill execution of JobRun ${jobRun.id}")
    // TODO: fill aborting logic here.
    persistenceActor ! Delete(jobRun)
    // TODO: we should switch to another receiver here to handle PersistFailed different
  }
}

object JobRunExecutorActor {

  case object KillCurrentJobRun
  case class JobRunUpdate(startedJobRun: StartedJobRun)

  case class JobRunFinished(jobResult: JobResult)
  case class JobRunAborted(jobResult: JobResult)

  def props(run: JobRun, promise: Promise[JobResult], repository: Repository[JobRunId, JobRun]): Props = Props(
    new JobRunExecutorActor(run, promise, repository)
  )
}

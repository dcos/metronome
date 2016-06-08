package dcos.metronome.jobrun.impl

import akka.actor._
import dcos.metronome.model.{ JobRun, JobRunId }
import dcos.metronome.repository.{ NoConcurrentRepoChange, Repository }

/**
  * Handles persistence for one JobExecutor.
  */
class JobRunPersistenceActor(id: JobRunId, repo: Repository[JobRunId, JobRun]) extends NoConcurrentRepoChange[JobRunId, JobRun, Unit] {
  import JobRunPersistenceActor._
  import context.dispatcher

  override def receive: Receive = {
    case Create(jobRun) => create(jobRun)
    case Update(change) => update(change)
    case Delete(orig)   => delete(orig)
  }

  def create(jobRun: JobRun): Unit = {
    log.debug(s"Create JobRun ${jobRun.id}")
    repoChange(repo.create(jobRun.id, jobRun), (), JobRunCreated, PersistFailed(_, id, _, _))
  }

  def update(change: JobRun => JobRun): Unit = {
    log.debug(s"Update JobRun $id")
    repoChange(repo.update(id, change), (), JobRunUpdated, PersistFailed(_, id, _, _))
  }

  def delete(orig: JobRun): Unit = {
    log.debug(s"Delete JobRun $id")
    repoChange(repo.delete(id).map(_ => orig), (), JobRunDeleted, PersistFailed(_, id, _, _))
  }
}

object JobRunPersistenceActor {
  import NoConcurrentRepoChange._

  case class Create(jobRun: JobRun)
  case class Update(change: JobRun => JobRun)
  case class Delete(orig: JobRun)

  //ack messages
  trait JobRunChange extends Change
  case class JobRunCreated(sender: ActorRef, jobRun: JobRun, nothing: Unit) extends JobRunChange
  case class JobRunUpdated(sender: ActorRef, jobRun: JobRun, nothing: Unit) extends JobRunChange
  case class JobRunDeleted(sender: ActorRef, jobRun: JobRun, nothing: Unit) extends JobRunChange
  case class PersistFailed(sender: ActorRef, id: JobRunId, ex: Throwable, nothing: Unit) extends Failed

  def props(id: JobRunId, repository: Repository[JobRunId, JobRun]): Props = {
    Props(new JobRunPersistenceActor(id, repository))
  }
}

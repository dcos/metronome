package dcos.metronome.history.impl

import akka.actor.{ Props, ActorRef }
import dcos.metronome.behavior.Behavior
import dcos.metronome.model.JobHistory
import dcos.metronome.repository.{ Repository, NoConcurrentRepoChange }
import mesosphere.marathon.state.PathId

class JobHistoryPersistenceActor(
    repo:         Repository[PathId, JobHistory],
    val behavior: Behavior
) extends NoConcurrentRepoChange[PathId, JobHistory, Unit] {
  import JobHistoryPersistenceActor._
  import context.dispatcher

  override def receive: Receive = around {
    case Create(id, jobRun) => create(id, jobRun)
    case Update(id, change) => update(id, change)
    case Delete(id, orig)   => delete(id, orig)
  }

  def create(id: PathId, jobRun: JobHistory): Unit = {
    log.debug(s"Create JobHistory ${jobRun.jobSpecId}")
    repoChange(repo.create(jobRun.jobSpecId, jobRun), (), JobHistoryCreated, PersistFailed(_, id, _, _))
  }

  def update(id: PathId, change: JobHistory => JobHistory): Unit = {
    log.debug(s"Update JobHistory $id")
    repoChange(repo.update(id, change), (), JobHistoryUpdated, PersistFailed(_, id, _, _))
  }

  def delete(id: PathId, orig: JobHistory): Unit = {
    log.debug(s"Delete JobHistory $id")
    repoChange(repo.delete(id).map(_ => orig), (), JobHistoryDeleted, PersistFailed(_, id, _, _))
  }
}

object JobHistoryPersistenceActor {
  import NoConcurrentRepoChange._

  case class Create(id: PathId, jobRun: JobHistory)
  case class Update(id: PathId, change: JobHistory => JobHistory)
  case class Delete(id: PathId, orig: JobHistory)

  //ack messages
  trait JobHistoryChange extends Change
  case class JobHistoryCreated(sender: ActorRef, jobHistory: JobHistory, nothing: Unit) extends JobHistoryChange
  case class JobHistoryUpdated(sender: ActorRef, jobHistory: JobHistory, nothing: Unit) extends JobHistoryChange
  case class JobHistoryDeleted(sender: ActorRef, jobHistory: JobHistory, nothing: Unit) extends JobHistoryChange
  case class PersistFailed(sender: ActorRef, id: PathId, ex: Throwable, nothing: Unit) extends Failed

  def props(repository: Repository[PathId, JobHistory], behavior: Behavior): Props = {
    Props(new JobHistoryPersistenceActor(repository, behavior))
  }
}

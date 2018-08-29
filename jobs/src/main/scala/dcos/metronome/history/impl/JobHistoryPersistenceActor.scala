package dcos.metronome
package history.impl

import akka.actor.{ ActorRef, Props }
import dcos.metronome.model.{ JobHistory, JobId }
import dcos.metronome.repository.{ NoConcurrentRepoChange, Repository }
import mesosphere.marathon.metrics.Metrics

class JobHistoryPersistenceActor(
  repo: Repository[JobId, JobHistory], metrics: Metrics) extends NoConcurrentRepoChange[JobId, JobHistory, Unit] {
  import JobHistoryPersistenceActor._
  import context.dispatcher

  private val createHistoryTimeMetric = metrics.timer("debug.persistence.history.create.duration")
  private val updateHistoryTimeMetric = metrics.timer("debug.persistence.history.update.duration")
  private val deleteHistoryTimeMetric = metrics.timer("debug.persistence.history.delete.duration")

  override def receive: Receive = {
    case Create(id, jobRun) => create(id, jobRun)
    case Update(id, change) => update(id, change)
    case Delete(id, orig)   => delete(id, orig)
  }

  def create(id: JobId, jobRun: JobHistory): Unit = createHistoryTimeMetric.blocking {
    log.debug(s"Create JobHistory ${jobRun.jobSpecId}")
    repoChange(repo.create(jobRun.jobSpecId, jobRun), (), JobHistoryCreated, PersistFailed(_, id, _, _))
  }

  def update(id: JobId, change: JobHistory => JobHistory): Unit = updateHistoryTimeMetric.blocking {
    log.debug(s"Update JobHistory $id")
    repoChange(repo.update(id, change), (), JobHistoryUpdated, PersistFailed(_, id, _, _))
  }

  def delete(id: JobId, orig: JobHistory): Unit = deleteHistoryTimeMetric.blocking {
    log.debug(s"Delete JobHistory $id")
    repoChange(repo.delete(id).map(_ => orig), (), JobHistoryDeleted, PersistFailed(_, id, _, _))
  }
}

object JobHistoryPersistenceActor {
  import NoConcurrentRepoChange._

  case class Create(id: JobId, jobRun: JobHistory)
  case class Update(id: JobId, change: JobHistory => JobHistory)
  case class Delete(id: JobId, orig: JobHistory)

  //ack messages
  trait JobHistoryChange extends Change
  case class JobHistoryCreated(sender: ActorRef, jobHistory: JobHistory, nothing: Unit) extends JobHistoryChange
  case class JobHistoryUpdated(sender: ActorRef, jobHistory: JobHistory, nothing: Unit) extends JobHistoryChange
  case class JobHistoryDeleted(sender: ActorRef, jobHistory: JobHistory, nothing: Unit) extends JobHistoryChange
  case class PersistFailed(sender: ActorRef, id: JobId, ex: Throwable, nothing: Unit) extends Failed

  def props(repository: Repository[JobId, JobHistory], metrics: Metrics): Props = {
    Props(new JobHistoryPersistenceActor(repository, metrics))
  }
}

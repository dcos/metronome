package dcos.metronome
package jobspec.impl

import akka.actor._
import dcos.metronome.model.{JobId, JobSpec}
import dcos.metronome.repository.NoConcurrentRepoChange.{Change, Failed}
import dcos.metronome.repository.{NoConcurrentRepoChange, Repository}
import mesosphere.marathon.metrics.Metrics

class JobSpecPersistenceActor(id: JobId, repo: Repository[JobId, JobSpec], metrics: Metrics)
    extends NoConcurrentRepoChange[JobId, JobSpec, ActorRef] {
  import JobSpecPersistenceActor._
  import context.dispatcher

  private val createJobSpecTimeMetric = metrics.timer("debug.persistence.job-spec.create.duration")
  private val updateJobSpecTimeMetric = metrics.timer("debug.persistence.job-spec.update.duration")
  private val deleteJobSpecTimeMetric = metrics.timer("debug.persistence.job-spec.delete.duration")

  override def receive: Receive = {
    case Create(jobSpec, delegate) => create(jobSpec, delegate)
    case Update(change, delegate) => update(change, delegate)
    case Delete(orig, delegate) => delete(orig, delegate)
  }

  def create(jobSpec: JobSpec, delegate: ActorRef): Unit =
    createJobSpecTimeMetric.blocking {
      log.info(s"Create JobSpec ${jobSpec.id}")
      repoChange(repo.create(jobSpec.id, jobSpec), delegate, Created, PersistFailed(_, id, _, _))
    }

  def update(change: JobSpec => JobSpec, delegate: ActorRef): Unit =
    updateJobSpecTimeMetric.blocking {
      log.info(s"Update JobSpec $id")
      repoChange(repo.update(id, change), delegate, Updated, PersistFailed(_, id, _, _))
    }

  def delete(orig: JobSpec, delegate: ActorRef): Unit =
    deleteJobSpecTimeMetric.blocking {
      log.info(s"Delete JobSpec $id")
      repoChange(repo.delete(id).map(_ => orig), delegate, Deleted, PersistFailed(_, id, _, _))
    }
}

object JobSpecPersistenceActor {

  case class Create(jobSpec: JobSpec, delegate: ActorRef)
  case class Update(change: JobSpec => JobSpec, delegate: ActorRef)
  case class Delete(orig: JobSpec, delegate: ActorRef)

  //ack messages
  sealed trait JobSpecChange extends Change {
    def sender: ActorRef
    def jobSpec: JobSpec
    def id: String = jobSpec.id.toString
  }
  case class Created(sender: ActorRef, jobSpec: JobSpec, delegate: ActorRef) extends JobSpecChange
  case class Updated(sender: ActorRef, jobSpec: JobSpec, delegate: ActorRef) extends JobSpecChange
  case class Deleted(sender: ActorRef, jobSpec: JobSpec, delegate: ActorRef) extends JobSpecChange

  case class PersistFailed(sender: ActorRef, id: JobId, ex: Throwable, delegate: ActorRef) extends Failed

  def props(id: JobId, repository: Repository[JobId, JobSpec], metrics: Metrics): Props = {
    Props(new JobSpecPersistenceActor(id, repository, metrics))
  }
}

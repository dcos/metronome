package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.behavior.Behavior
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.NoConcurrentRepoChange.{ Failed, Change }
import dcos.metronome.repository.{ NoConcurrentRepoChange, Repository }
import mesosphere.marathon.state.PathId

class JobSpecPersistenceActor(
    id:           PathId,
    repo:         Repository[PathId, JobSpec],
    val behavior: Behavior
) extends NoConcurrentRepoChange[PathId, JobSpec, ActorRef] {
  import JobSpecPersistenceActor._
  import context.dispatcher

  override def receive: Receive = around {
    case Create(jobSpec, delegate) => create(jobSpec, delegate)
    case Update(change, delegate)  => update(change, delegate)
    case Delete(orig, delegate)    => delete(orig, delegate)
  }

  def create(jobSpec: JobSpec, delegate: ActorRef): Unit = {
    log.info(s"Create JobSpec ${jobSpec.id}")
    repoChange(repo.create(jobSpec.id, jobSpec), delegate, Created, PersistFailed(_, id, _, _))
  }

  def update(change: JobSpec => JobSpec, delegate: ActorRef): Unit = {
    log.info(s"Update JobSpec $id")
    repoChange(repo.update(id, change), delegate, Updated, PersistFailed(_, id, _, _))
  }

  def delete(orig: JobSpec, delegate: ActorRef): Unit = {
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
    def id: String = jobSpec.id.toString()
  }
  case class Created(sender: ActorRef, jobSpec: JobSpec, delegate: ActorRef) extends JobSpecChange
  case class Updated(sender: ActorRef, jobSpec: JobSpec, delegate: ActorRef) extends JobSpecChange
  case class Deleted(sender: ActorRef, jobSpec: JobSpec, delegate: ActorRef) extends JobSpecChange

  case class PersistFailed(sender: ActorRef, id: PathId, ex: Throwable, delegate: ActorRef) extends Failed

  def props(id: PathId, repository: Repository[PathId, JobSpec], behavior: Behavior): Props = {
    Props(new JobSpecPersistenceActor(id, repository, behavior))
  }
}

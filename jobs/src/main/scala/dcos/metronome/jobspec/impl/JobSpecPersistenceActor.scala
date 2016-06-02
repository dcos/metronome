package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.NoConcurrentRepoChange.{ Failed, Change }
import dcos.metronome.repository.{ NoConcurrentRepoChange, Repository }
import mesosphere.marathon.state.PathId

import scala.concurrent.Promise

class JobSpecPersistenceActor(id: PathId, repo: Repository[PathId, JobSpec]) extends NoConcurrentRepoChange[PathId, JobSpec, Promise[JobSpec]] {
  import JobSpecPersistenceActor._
  import context.dispatcher

  override def receive: Receive = {
    case Create(jobSpec, promise) => create(jobSpec, promise)
    case Update(change, promise)  => update(change, promise)
    case Delete(orig, promise)    => delete(orig, promise)
  }

  def create(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    log.info(s"Create JobSpec ${jobSpec.id}")
    repoChange(repo.create(jobSpec.id, jobSpec), promise, Created, PersistFailed(_, id, _, _))
  }

  def update(change: JobSpec => JobSpec, promise: Promise[JobSpec]): Unit = {
    log.info(s"Update JobSpec $id")
    repoChange(repo.update(id, change), promise, Updated, PersistFailed(_, id, _, _))
  }

  def delete(orig: JobSpec, promise: Promise[JobSpec]): Unit = {
    log.info(s"Delete JobSpec $id")
    repoChange(repo.delete(id).map(_ => orig), promise, Deleted, PersistFailed(_, id, _, _))
  }
}

object JobSpecPersistenceActor {

  case class Create(jobSpec: JobSpec, promise: Promise[JobSpec])
  case class Update(change: JobSpec => JobSpec, promise: Promise[JobSpec])
  case class Delete(orig: JobSpec, promise: Promise[JobSpec])

  //ack messages
  sealed trait JobSpecChange extends Change {
    def sender: ActorRef
    def jobSpec: JobSpec
    def id: String = jobSpec.id.toString()
  }
  case class Created(sender: ActorRef, jobSpec: JobSpec, promise: Promise[JobSpec]) extends JobSpecChange
  case class Updated(sender: ActorRef, jobSpec: JobSpec, promise: Promise[JobSpec]) extends JobSpecChange
  case class Deleted(sender: ActorRef, jobSpec: JobSpec, promise: Promise[JobSpec]) extends JobSpecChange

  case class PersistFailed(sender: ActorRef, id: PathId, ex: Throwable, promise: Promise[JobSpec]) extends Failed

  def props(id: PathId, repository: Repository[PathId, JobSpec]): Props = {
    Props(new JobSpecPersistenceActor(id, repository))
  }
}

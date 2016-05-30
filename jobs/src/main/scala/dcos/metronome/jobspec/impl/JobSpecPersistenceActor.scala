package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.Repository
import mesosphere.marathon.state.PathId

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

class JobSpecPersistenceActor(id: PathId, repo: Repository[PathId, JobSpec]) extends Actor with ActorLogging with Stash {
  import JobSpecPersistenceActor._
  import context.dispatcher

  override def receive: Receive = {
    case Create(jobSpec, promise) => create(jobSpec, promise)
    case Update(change, promise)  => update(change, promise)
    case Delete(orig, promise)    => delete(orig, promise)
  }

  def create(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    log.info(s"Create JobSpec ${jobSpec.id}")
    repoChange(repo.create(jobSpec.id, jobSpec), promise, Created)
  }

  def update(change: JobSpec => JobSpec, promise: Promise[JobSpec]): Unit = {
    log.info(s"Update JobSpec $id")
    repoChange(repo.update(id, change), promise, Updated)
  }

  def delete(orig: JobSpec, promise: Promise[JobSpec]): Unit = {
    log.info(s"Delete JobSpec $id")
    repoChange(repo.delete(id).map(_ => orig), promise, Deleted)
  }

  def repoChange(
    change:    Future[JobSpec],
    promise:   Promise[JobSpec],
    onSuccess: (ActorRef, JobSpec, Promise[JobSpec]) => Result
  ): Unit = {
    context.become(waitForPersisted)
    val actor = self
    val from = sender()
    change.onComplete {
      case Success(result) => actor ! onSuccess(from, result, promise)
      case Failure(ex)     => actor ! PersistFailed(from, id, ex, promise)
    }
  }

  def waitForPersisted: Receive = {
    case event: PersistFailed =>
      log.error(event.ex, "Repository change failed")
      context.become(receive)
      event.sender ! event
      unstashAll()
    case event: Result =>
      log.info(s"Repository change on ${event.jobSpec.id} successful")
      context.become(receive)
      event.sender ! event
      unstashAll()
    case _ => stash()
  }
}

object JobSpecPersistenceActor {

  case class Create(jobSpec: JobSpec, promise: Promise[JobSpec])
  case class Update(change: JobSpec => JobSpec, promise: Promise[JobSpec])
  case class Delete(orig: JobSpec, promise: Promise[JobSpec])

  //ack messages
  sealed trait Result {
    def sender: ActorRef
    def jobSpec: JobSpec
  }
  case class Created(sender: ActorRef, jobSpec: JobSpec, promise: Promise[JobSpec]) extends Result
  case class Updated(sender: ActorRef, jobSpec: JobSpec, promise: Promise[JobSpec]) extends Result
  case class Deleted(sender: ActorRef, jobSpec: JobSpec, promise: Promise[JobSpec]) extends Result

  case class PersistFailed(sender: ActorRef, id: PathId, ex: Throwable, promise: Promise[JobSpec])

  def props(id: PathId, repository: Repository[PathId, JobSpec]): Props = {
    Props(new JobSpecPersistenceActor(id, repository))
  }
}

package dcos.metronome.jobspec.impl

import akka.actor.{ Stash, Props, ActorLogging, Actor }
import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

class JobSpecPersistenceActor(repo: JobSpecRepository) extends Actor with ActorLogging with Stash {
  import JobSpecPersistenceActor._
  import context.dispatcher

  override def receive: Receive = {
    case Create(jobSpec, promise)  => repoChange(repo.create(jobSpec.id.safePath, jobSpec), promise)
    case Update(id, to, promise)   => repoChange(repo.update(id.safePath, to), promise)
    case Delete(id, orig, promise) => repoChange(repo.delete(id.safePath).map(_ => orig), promise)
  }

  def repoChange(change: Future[JobSpec], promise: Promise[JobSpec]): Unit = {
    context.become(waitForPersisted)
    val actor = self
    change.onComplete {
      case Success(result) =>
        promise.success(result)
        actor ! JobSpecPersisted(result)
      case Failure(ex) =>
        promise.failure(ex)
        actor ! JobSpecPersistFailed
    }
  }

  def waitForPersisted: Receive = {
    case event: JobSpecPersisted =>
      context.become(receive)
      context.parent ! event
      unstashAll()
    case JobSpecPersistFailed =>
      context.become(receive)
      context.parent ! JobSpecPersistFailed
      unstashAll()
    case _ => stash()
  }
}

object JobSpecPersistenceActor {

  case class Create(jobSpec: JobSpec, promise: Promise[JobSpec])
  case class Update(id: PathId, to: JobSpec, promise: Promise[JobSpec])
  case class Delete(id: PathId, orig: JobSpec, promise: Promise[JobSpec])

  case class JobSpecPersisted(spec: JobSpec)
  case object JobSpecPersistFailed

  def props(repository: JobSpecRepository): Props = Props(new JobSpecPersistenceActor(repository))
}

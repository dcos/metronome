package dcos.metronome.repository

import akka.actor.{ Stash, ActorLogging, Actor, ActorRef }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait NoConcurrentRepoChange[Id, Model, Data] extends Actor with ActorLogging with Stash {
  import NoConcurrentRepoChange._

  def repoChange(
    change:    Future[Model],
    data:      Data,
    onSuccess: (ActorRef, Model, Data) => Change,
    onFailed:  (ActorRef, Throwable, Data) => Failed
  )(implicit ec: ExecutionContext): Unit = {
    context.become(waitForPersisted)
    val actor = self
    val from = sender()
    change.onComplete {
      case Success(result) => actor ! onSuccess(from, result, data)
      case Failure(ex)     => actor ! onFailed(from, ex, data)
    }
  }

  def waitForPersisted: Receive = {
    case event: Failed =>
      log.error(event.ex, "Repository change failed")
      //TODO: use become/unbecome
      context.become(receive)
      event.sender ! event
      unstashAll()
    case event: Change =>
      log.debug(s"Repository change on ${event.id} successful")
      //TODO: use become/unbecome
      context.become(receive)
      event.sender ! event
      unstashAll()
    case _ => stash()
  }

}

object NoConcurrentRepoChange {

  trait Change {
    def sender: ActorRef
    def id: String
  }

  trait Failed {
    def sender: ActorRef
    def ex: Throwable
  }
}

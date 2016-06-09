package dcos.metronome.repository

import akka.actor.{ Stash, ActorLogging, Actor, ActorRef }
import dcos.metronome.behavior.ActorMetrics

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

trait NoConcurrentRepoChange[Id, Model, Data] extends Actor with ActorLogging with Stash with ActorMetrics {
  import NoConcurrentRepoChange._

  def repoChange(
    change:    => Future[Model],
    data:      Data,
    onSuccess: (ActorRef, Model, Data) => Change,
    onFailed:  (ActorRef, Throwable, Data) => Failed
  )(implicit ec: ExecutionContext): Unit = {
    val from = sender()
    try {
      val changed = change //can throw an exception, so execute before we enter waiting state
      context.become(waitForPersisted)
      val actor = self
      changed.onComplete {
        case Success(result) => actor ! onSuccess(from, result, data)
        case Failure(ex)     => actor ! onFailed(from, ex, data)
      }
    } catch {
      case NonFatal(ex) =>
        log.error(ex, "Could not apply repository change")
        from ! onFailed(from, ex, data)
    }
  }

  def waitForPersisted: Receive = around {
    case event: Failed =>
      log.error(event.ex, "Repository change failed")
      //TODO: use become/unbecome
      context.become(receive)
      event.sender ! event
      unstashAll()
    case event: Change =>
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
  }

  trait Failed {
    def sender: ActorRef
    def ex: Throwable
  }
}

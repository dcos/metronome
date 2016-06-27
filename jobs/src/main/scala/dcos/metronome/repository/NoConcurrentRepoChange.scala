package dcos.metronome.repository

import akka.actor.{ Stash, ActorLogging, Actor, ActorRef }
import dcos.metronome.behavior.ActorBehavior

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

trait NoConcurrentRepoChange[Id, Model, Data] extends Actor with ActorLogging with Stash with ActorBehavior {
  import NoConcurrentRepoChange._

  final def repoChange(
    change:    => Future[Model],
    data:      Data,
    onSuccess: (ActorRef, Model, Data) => Change,
    onFailed:  (ActorRef, Throwable, Data) => Failed
  )(implicit ec: ExecutionContext): Unit = {
    val from = sender()
    try {
      val changed = change //can throw an exception, so execute before we enter waiting state
      context.become(waitForPersisted, discardOld = false)
      changed.onComplete {
        case Success(result) => self ! onSuccess(from, result, data)
        case Failure(ex)     => self ! onFailed(from, ex, data)
      }
    } catch {
      case NonFatal(ex) =>
        log.error(ex, "Could not apply repository change")
        notifySender(from, onFailed(from, ex, data))
    }
  }

  private[this] def waitForPersisted: Receive = around {
    case event: Failed =>
      log.error(event.ex, "Repository change failed")
      notifySender(event.sender, event)
    case event: Change =>
      notifySender(event.sender, event)
    case _ => stash()
  }

  private[this] def notifySender(recipient: ActorRef, message: Any): Unit = {
    context.unbecome()
    recipient ! message
    unstashAll()
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

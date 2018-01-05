package dcos.metronome
package repository

import akka.actor.{ Actor, ActorLogging, Stash }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait LoadContentOnStartup[Id, Model] extends Actor with Stash with ActorLogging {
  import LoadContentOnStartup._

  //TODO: change me to zk ec
  import context.dispatcher

  override def preStart(): Unit = {
    super.preStart()
    context.become(waitForInit)
    loadAll()
  }

  def repo: Repository[Id, Model]
  def initialize(specs: List[Model]): Unit

  def waitForInit: Receive = {
    case init: Init[Model] =>
      initialize(init.result)
      context.become(receive)
      unstashAll()
    case _ => stash()
  }

  def loadAll(): Unit = {
    val loadAllFuture = repo.ids().flatMap { ids =>
      ids.foldLeft(Future.successful(List.empty[Model])) {
        case (resultsFuture, id) => resultsFuture.flatMap { res =>
          repo.get(id).map(_.map(_ :: res).getOrElse(res))
        }
      }
    }
    val me = self
    loadAllFuture.onComplete {
      case Success(result) => me ! Init(result)
      case Failure(ex) =>
        log.error(ex, "Can not load initial data. Give up.")
        throw ex
    }
  }
}

object LoadContentOnStartup {
  case class Init[T](result: List[T])
}

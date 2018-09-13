package dcos.metronome
package repository

import akka.actor.{ Actor, ActorLogging, Stash }
import mesosphere.marathon.StoreCommandFailedException
import org.apache.zookeeper.KeeperException.NoNodeException

import scala.concurrent.Future
import scala.util.control.NonFatal
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
      Future.sequence(ids.map(id => getModel(id))).map(_.flatten.toList)
    }
    val me = self
    loadAllFuture.onComplete {
      case Success(result) => me ! Init(result)
      case Failure(ex) =>
        log.error(ex, "Can not load initial data. Give up.")
        System.exit(-1)
    }
  }

  private def getModel(id: Id): Future[Option[Model]] = {
    repo.get(id).recoverWith {
      case ex: StoreCommandFailedException =>
        ex.getCause match {
          case cause: NoNodeException =>
            log.error(s"ID $id or job-specs znode missing. Zk will need to be manually repaired.  Exception message: ${cause.getMessage}")
            Future.successful(None)
          case NonFatal(cause) =>
            log.error(s"Unexpected exception occurred in reading zk at startup.  Exception message: ${cause.getMessage}")
            // We need crash strategy similar to marathon, for now we can NOT continue with such a zk failure.
            System.exit(-1)
            Future.failed(cause)
        }
    }
  }
}

object LoadContentOnStartup {
  case class Init[T](result: List[T])
}

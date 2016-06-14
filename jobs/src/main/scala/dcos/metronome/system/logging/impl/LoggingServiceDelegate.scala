package dcos.metronome.system.logging.impl

import akka.actor.ActorRef
import ch.qos.logback.classic.Logger
import dcos.metronome.jobspec.impl.JobSpecServiceActor.CreateJobSpec
import dcos.metronome.model.JobSpec
import dcos.metronome.system.logging.LoggingService
import dcos.metronome.system.logging.impl.LoggingServiceActor.{GetLogger, LoggerList}

import scala.concurrent.{ Promise, Future }

/**
  * Created by stas on 10.06.16.
  */
class LoggingServiceDelegate(actorRef: ActorRef) extends LoggingService {
  override def loggerList(): Future[Iterable[Logger]] = {
    val promise = Promise[Iterable[Logger]]
    actorRef ! LoggerList(promise)
    promise.future
  }

  override def getLogger(name: String): Future[Option[Logger]] =  {
    val promise = Promise[Option[Logger]]
    actorRef ! GetLogger(name, promise)
    promise.future
  }

  override def setLogLevel(name: String, logLevel: String): Unit = ???
}

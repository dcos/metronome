package dcos.metronome.system.logging.impl

import akka.actor.{ Actor, Props }
import ch.qos.logback.classic.{ Level, Logger, LoggerContext }

import scala.collection.JavaConverters._
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

/**
  * Created by stas on 10.06.16.
  */
class LoggingServiceActor extends Actor {

  import dcos.metronome.system.logging.impl.LoggingServiceActor._
  import context.dispatcher

  val factory: LoggerContext = org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  override def receive: Receive = {
    case LoggerList(promise)                                  => loggerList(promise)
    case GetLogger(name, promise)                             => getLogger(name, promise)
    case SetLogLevel(name, level, promise)                    => setLogLevel(name, level, promise)
    case ScheduledSetLogLevel(name, level, duration, promise) => scheduleSetLogLevel(name, level, duration, promise)
  }

  def scheduleSetLogLevel(name: String, level: Level, duration: FiniteDuration, promise: Promise[Logger]): Unit = {
    val logger = factory.getLogger(name)
    val currentLevel = logger.getLevel
    logger.setLevel(level)
    context.system.scheduler.scheduleOnce(duration, self, SetLogLevel(name, currentLevel, Promise[Logger]))
    promise.success(logger)
  }

  def setLogLevel(name: String, level: Level, promise: Promise[Logger]): Unit = {
    val logger = factory.getLogger(name)
    logger.setLevel(level)
    promise.success(logger)
  }

  def getLogger(name: String, promise: Promise[Option[Logger]]): Unit = {
    val logger = factory.getLogger(name)
    promise.success(logger)
  }

  def loggerList(promise: Promise[Iterable[Logger]]): Unit = {
    val loggerList = factory.getLoggerList.asScala
    promise.success(loggerList)
  }
}

object LoggingServiceActor {

  case class LoggerList(promise: Promise[Iterable[Logger]])
  case class GetLogger(name: String, promise: Promise[Option[Logger]])
  case class SetLogLevel(name: String, level: Level, promise: Promise[Logger])
  case class ScheduledSetLogLevel(name: String, level: Level, duration: FiniteDuration, promise: Promise[Logger])

  def props(): Props = Props(new LoggingServiceActor())
}
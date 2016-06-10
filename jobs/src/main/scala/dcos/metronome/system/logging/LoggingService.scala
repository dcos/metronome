package dcos.metronome.system.logging

import ch.qos.logback.classic.Logger

import scala.concurrent.Future

/**
  * Created by stas on 10.06.16.
  */
trait LoggingService {

  def loggerList(): Future[Iterable[Logger]]

  def getLogger(name: String): Future[Logger]

  def setLogLevel(name: String, logLevel: String): Future[Logger]

}

package dcos.metronome.api.v1.controllers

import ch.qos.logback.classic.LoggerContext
import dcos.metronome.api.RestController
import dcos.metronome.api.v1.models._
import mesosphere.marathon.state.Timestamp
import org.slf4j.Logger
import play.api.mvc.Action

import scala.collection.JavaConverters._

class LoggerController extends RestController {

  def loggerList = Action { implicit request =>
    try {
      val factory: LoggerContext = org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
      val loggerList = factory.getLoggerList.asScala
      Ok(loggerList)
    } catch {
      case e: Exception => Ok("fail: " + e.getMessage)
    }
  }

  def getLogger(name: String = Logger.ROOT_LOGGER_NAME) = Action { implicit request =>
    try {
      val factory: LoggerContext = org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
      val logger = factory.getLogger(name)
      Ok(logger)
    } catch {
      case e: Exception => Ok("fail: " + e.getMessage)
    }
  }

  def setLogLevel(name: String, logLevel: String) = Action { implicit request =>
    try {
      val factory: LoggerContext = org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
      val logger = factory.getLogger(name)
      val targetLogLevel = ch.qos.logback.classic.Level.valueOf(logLevel)
      val currentLogLevel = logger.getLevel
      logger.setLevel(targetLogLevel)

      Ok(logger)
    } catch {
      case e: Exception => Ok("fail: " + e.getMessage)
    }
  }

  private def scheduleLogLevelUpdate(name: String, logLevel: String, deu: Timestamp): Unit = {

  }
}

package dcos.metronome.api

import org.slf4j.LoggerFactory
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Result, Results }

import scala.concurrent.Future

class ErrorHandler extends HttpErrorHandler {

  val log = LoggerFactory.getLogger(getClass)

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    log.debug(s"Client Error on path ${request.path}. Message: $message Status: $statusCode")
    val json = Json.obj("message" -> message, "requestPath" -> request.path)
    Future.successful(Results.Status(statusCode)(json))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    log.error(s"Error serving ${request.path}", exception)
    val json = Json.obj("requestPath" -> request.path)
    Future.successful(Results.Status(INTERNAL_SERVER_ERROR)(json))
  }
}

package dcos.metronome.api

import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Result, Results }

import scala.concurrent.Future

class ErrorHandler extends HttpErrorHandler {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val json = Json.obj("message" -> message, "requestPath" -> request.path)
    Future.successful(Results.Status(statusCode)(json))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    val json = Json.obj("message" -> exception.getMessage, "requestPath" -> request.path)
    Future.successful(Results.Status(INTERNAL_SERVER_ERROR)(json))
  }
}

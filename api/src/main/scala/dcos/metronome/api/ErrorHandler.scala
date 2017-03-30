package dcos.metronome.api

import org.slf4j.LoggerFactory
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Result, Results }
import play.twirl.api.HtmlFormat

import scala.concurrent.Future

class ErrorHandler extends HttpErrorHandler {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    log.debug(s"Client Error on path ${request.path}. Message: $message Status: $statusCode")
    val json = Json.obj("message" -> escape(message), "requestPath" -> escape(request.path))
    Future.successful(Results.Status(statusCode)(json))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    log.error(s"Error serving ${request.path}", exception)
    val json = Json.obj("requestPath" -> escape(request.path))
    Future.successful(Results.Status(INTERNAL_SERVER_ERROR)(json))
  }

  /**
    * Possible cross site scripting vulnerability: the message could contain html tags, interpreted by a browser.
    * Example:
    *      Send: GET /"><script>alert(document.domain)</script>
    *      Receive: {"message":"Illegal character in path at index 1: /\"><script>alert(document.domain)</script>","requestPath":"/\"><script>alert(document.domain)</script>"}
    * To prevent possible interpretation of the message: all strings get HTML escaped
    */
  private def escape(msg: String): String = HtmlFormat.escape(msg).body
}

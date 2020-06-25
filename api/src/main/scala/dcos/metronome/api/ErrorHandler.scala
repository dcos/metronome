package dcos.metronome
package api

import org.slf4j.LoggerFactory
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Result, Results}
import play.twirl.api.HtmlFormat

import scala.concurrent.Future

class ErrorHandler extends HttpErrorHandler {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    log.debug(s"Client Error on path ${request.path}. Message: $message Status: $statusCode")
    val outputMessage = if (statusCode == NOT_FOUND) { ErrorHandler.noRouteHandlerMessage }
    else { message }
    val json = Json.obj("message" -> escape(outputMessage), "requestPath" -> escape(request.path))
    Future.successful(Results.Status(statusCode)(json))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {

    /*
      When a non-leading metronome is connected to by a client via the TLS port (9443 by default) and the leading master
      registers with the plain http port, the proxy tries to redirect a TLS connection to the plain http port.
      This results in the error which is expected and can be verbose in the logs.

      The recommendation is to disable the plain HTTP port and always use TLS in this situation.   The condition is also
      detected below and the verbose stacktrace is not logged.
     */
    if (exception.getMessage.contains("not an SSL/TLS record")) {
      log.info(
        s"Client trying to connect via TLS port, but the proxying only happen through plain HTTP port. " +
          s"Please use plain HTTP or query the leader directly. Error serving ${request.path}.  Exception Msg: ${exception.getMessage}"
      )
    } else {
      log.error(s"Error serving ${request.path}", exception)
    }

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

object ErrorHandler {
  val noRouteHandlerMessage = "Unknown route."
}

package dcos.metronome.api.v1

import akka.util.ByteString
import dcos.metronome.api.ApiConfig
import mesosphere.marathon.core.election.ElectionService
import org.slf4j.LoggerFactory
import play.api.http.HttpEntity
import play.api.libs.streams.Accumulator
import play.api.libs.ws.{ WSClient, StreamedBody }
import play.api.mvc._

class LeaderProxyFilter(ws: WSClient, electionService: ElectionService, config: ApiConfig) extends EssentialFilter with Results {

  import LeaderProxyFilter._
  import scala.concurrent.ExecutionContext.Implicits.global
  val log = LoggerFactory.getLogger(getClass)

  val localHostPort = config.hostname + (if (config.disableHttp) config.httpsPort else config.httpPort)
  val localRoutes = Set("/ping", "/v1/metrics")

  override def apply(next: EssentialAction): EssentialAction = new EssentialAction {
    override def apply(request: RequestHeader): Accumulator[ByteString, Result] = {
      def isProxiedToSelf = request.headers.get(HEADER_VIA).contains(localHostPort)
      def doNotProxy() = localRoutes(request.path)
      if (electionService.isLeader || doNotProxy()) {
        next(request)
      } else if (isProxiedToSelf) {
        Accumulator.done(BadRequest("Prevent proxying already proxied request"))
      } else {
        electionService.leaderHostPort match {
          case Some(hostPort) => proxyRequest(request, hostPort)
          case None           => Accumulator.done(ServiceUnavailable("No consistent leadership"))
        }
      }
    }
  }

  def proxyRequest(request: RequestHeader, leaderHostPort: String): Accumulator[ByteString, Result] = {
    log.info(s"Proxy request ${request.path} to $leaderHostPort")
    val headers = request.headers.headers ++ Seq(HEADER_LEADER -> leaderHostPort, HEADER_VIA -> localHostPort)
    val scheme = if (request.secure) "https" else "http"
    Accumulator.source[ByteString].mapFuture { source =>
      ws.url(s"$scheme://$leaderHostPort${request.path}?${request.rawQueryString}")
        .withMethod(request.method)
        .withHeaders(headers: _*)
        .withRequestTimeout(config.leaderProxyTimeout)
        .withBody(StreamedBody(source))
        .execute()
        .map{ r =>
          val proxyHeaders = Map(HEADER_LEADER -> leaderHostPort, HEADER_VIA -> localHostPort)
          val responseHeaders = r.allHeaders.map{ case (k, v) => k -> v.mkString(", ") }
          val header = ResponseHeader(r.status, proxyHeaders ++ responseHeaders, Some(r.statusText))
          val body = HttpEntity.Strict(r.bodyAsBytes, None)
          Result(header, body)
        }
    }
  }
}

object LeaderProxyFilter {
  val HEADER_VIA = "X-VIA"
  val HEADER_LEADER = "X-LEADER"
}


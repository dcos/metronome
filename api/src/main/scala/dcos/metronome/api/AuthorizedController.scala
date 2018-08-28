package dcos.metronome
package api

import akka.stream.Materializer
import akka.util.ByteString
import dcos.metronome.jobinfo.JobSpecSelector
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.JobRunStatus.Success
import dcos.metronome.model.{ JobRun, JobSpec, QueuedJobRunInfo }
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.plugin.http.{ HttpRequest, HttpResponse }
import play.api.http.{ HeaderNames, HttpEntity, Status }
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
  * A request that adds the User for the current call
  */
case class AuthorizedRequest[Body](identity: Identity, request: Request[Body], authorizer: Authorizer) extends WrappedRequest(request) with Results {

  def authorizedAsync[Auth >: Body](action: AuthorizedAction[Auth])(block: Body => Future[Result]): Future[Result] = {
    if (authorizer.isAuthorized(identity, action, request.body)) block(request.body)
    else Future.successful(notAuthorized())
  }

  def authorizedAsync[Auth, Resource <: Auth](action: AuthorizedAction[Auth], resource: Resource)(block: Resource => Future[Result]): Future[Result] = {
    if (authorizer.isAuthorized(identity, action, resource)) block(resource)
    else Future.successful(notAuthorized())
  }

  def authorized[Auth](action: AuthorizedAction[Auth], resource: Auth, block: => Result): Result = {
    if (authorizer.isAuthorized(identity, action, resource)) block
    else notAuthorized()
  }

  def selectAuthorized = new JobSpecSelector {
    override def matches(jobSpec: JobSpec): Boolean = isAllowed(jobSpec)
  }
  def isAllowed(jobSpec: JobSpec): Boolean = authorizer.isAuthorized(identity, ViewRunSpec, jobSpec)
  def isAllowed(jobRun: JobRun): Boolean = isAllowed(jobRun.jobSpec)
  def isAllowed(started: StartedJobRun): Boolean = isAllowed(started.jobRun.jobSpec)
  // QueuedJobRunInfo extends RunSpec so we can pass it directly to Authorizer
  def isAllowed(queuedJobRunInfo: QueuedJobRunInfo): Boolean = authorizer.isAuthorized(identity, ViewRunSpec, queuedJobRunInfo)

  object Authorized {
    def unapply(jobSpec: JobSpec): Option[JobSpec] = Some(jobSpec).filter(isAllowed)
  }

  def notAuthorized(): Result = PluginFacade.withResponse(authorizer.handleNotAuthorized(identity, _))
}

trait AuthorizedController extends RestController {

  def authenticator: Authenticator
  def authorizer: Authorizer
  def config: ApiConfig
  def defaultBodyParser: BodyParser[AnyContent]
  def metrics: Metrics

  implicit val mat: Materializer

  //play default execution context
  import play.api.libs.concurrent.Execution.Implicits._

  /**
    * Use this object to create an authorized action.
    */
  object AuthorizedAction extends AuthorizedActionBuilder {
    def apply() = new AuthorizedActionBuilder(None)
    def apply(identity: Identity) = new AuthorizedActionBuilder(Some(identity))
  }

  class AuthorizedActionBuilder(authorize: Option[Identity] = None) extends ActionBuilder[AuthorizedRequest, AnyContent] {

    def invokeBlock[A](request: Request[A], block: AuthorizedRequest[A] => Future[Result]) = {
      val facade = PluginFacade.withRequest(request, config)
      def notAuthenticated = PluginFacade.withResponse(authenticator.handleNotAuthenticated(facade, _))
      authenticator.authenticate(facade).flatMap {
        case Some(identity) => block(AuthorizedRequest(identity, request, authorizer))
        case None           => Future.successful(notAuthenticated)
      }
    }

    override def parser: BodyParser[AnyContent] = defaultBodyParser

    override protected def executionContext: ExecutionContext = ExecutionContext.global
  }

  private[this] val http1XX = metrics.counter("http.responses.1xx.rate")
  private[this] val http2XX = metrics.counter("http.responses.2xx.rate")
  private[this] val http3XX = metrics.counter("http.responses.3xx.rate")
  private[this] val http4XX = metrics.counter("http.responses.4xx.rate")
  private[this] val http5XX = metrics.counter("http.responses.5xx.rate")
  private[this] val apiErrors = metrics.counter("http.responses.errors.rate")

  val measuredAction: MeasuredActionBuilder = new MeasuredActionBuilder

    class MeasuredActionBuilder() extends ActionBuilder[Request, AnyContent] {

    def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]) = {
      val result = block(request)
      result.onComplete {
        case Success(response) =>
          response.header.status match {
            case status if status < 200  => http1XX.increment()
            case status if status < 300  => http2XX.increment()
            case status if status < 400  => http3XX.increment()
            case status if status < 500  => http4XX.increment()
            case status if status >= 500 => http5XX.increment()
          }
        case Failure(_) => apiErrors.increment()
      }

      result
    }

    override def parser: BodyParser[AnyContent] = defaultBodyParser

    override protected def executionContext: ExecutionContext = ExecutionContext.global
  }
}

object PluginFacade {

  def withRequest(request: RequestHeader, config: ApiConfig): HttpRequest = new HttpRequest {
    override def method: String = request.method
    override def requestPath: String = request.path
    override def header(name: String): Seq[String] = request.headers.getAll(name).to[Seq]
    override def cookie(name: String): Option[String] = request.cookies.get(name).map(_.value)
    override def queryParam(name: String): Seq[String] = request.getQueryString(name).to[Seq]
    override def remoteAddr: String = request.remoteAddress
    override def remotePort: Int = 0 //not available
    override def localPort: Int = config.effectivePort
    override def localAddr: String = config.hostname
  }

  def withResponse(fn: HttpResponse => Unit): Result = {
    val facade = new PluginResponse
    fn(facade)
    facade.result
  }

  private[this] class PluginResponse extends HttpResponse with Status with HeaderNames {
    var result: Result = Result.apply(ResponseHeader.apply(UNAUTHORIZED), HttpEntity.NoEntity)

    override def header(header: String, value: String): Unit = {
      result = result.withHeaders(header -> value)
    }
    override def body(mediaType: String, bytes: Array[Byte]): Unit = {
      result = result.copy(body = HttpEntity.Strict(ByteString.apply(bytes), Some(mediaType)))
    }
    override def sendRedirect(url: String): Unit = {
      result = result.withHeaders(LOCATION -> url)
    }
    override def cookie(name: String, value: String, maxAge: Int, secure: Boolean): Unit = {
      result = result.withCookies(Cookie(name, value, maxAge = Some(maxAge), secure = secure))
    }
    override def status(code: Int): Unit = {
      result = result.copy(result.header.copy(status = code))
    }
  }
}

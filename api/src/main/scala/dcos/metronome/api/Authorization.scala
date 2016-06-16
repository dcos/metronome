package dcos.metronome.api

import akka.util.ByteString
import dcos.metronome.jobinfo.JobSpecSelector
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobRun, JobSpec }
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.plugin.http.{ HttpRequest, HttpResponse }
import play.api.http.{ HeaderNames, HttpEntity, Status }
import play.api.mvc._

import scala.concurrent.Future

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

  object Authorized {
    def unapply(jobSpec: JobSpec): Option[JobSpec] = if (isAllowed(jobSpec)) Some(jobSpec) else None
  }

  def notAuthorized(): Result = PluginFacade.withResponse(authorizer.handleNotAuthorized(identity, _))
}

trait Authorization extends RestController {

  def authenticator: Authenticator
  def authorizer: Authorizer

  //play default execution context
  import play.api.libs.concurrent.Execution.Implicits._

  /**
    * Use this object to create an authorized action.
    */
  object AuthorizedAction extends AuthorizedActionBuilder {
    def apply() = new AuthorizedActionBuilder(None)
    def apply(identity: Identity) = new AuthorizedActionBuilder(Some(identity))
  }

  class AuthorizedActionBuilder(authorize: Option[Identity] = None) extends ActionBuilder[AuthorizedRequest] {

    def invokeBlock[A](request: Request[A], block: AuthorizedRequest[A] => Future[Result]) = {
      val facade = PluginFacade.withRequest(request)
      def notAuthenticated = PluginFacade.withResponse(authenticator.handleNotAuthenticated(facade, _))
      authenticator.authenticate(facade).flatMap {
        case Some(identity) => block(AuthorizedRequest(identity, request, authorizer))
        case None           => Future.successful(notAuthenticated)
      }
    }
  }
}

object PluginFacade {

  def withRequest(request: RequestHeader): HttpRequest = new HttpRequest {
    override def method: String = request.method
    override def requestPath: String = request.path
    override def remoteAddr: String = request.remoteAddress
    override def header(name: String): Seq[String] = request.headers.getAll(name)
    override def cookie(name: String): Option[String] = request.cookies.get(name).map(_.value)
    override def queryParam(name: String): Seq[String] = request.getQueryString(name).toSeq
    // TODO: implement these
    override def remotePort: Int = ???
    override def localPort: Int = ???
    override def localAddr: String = ???
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


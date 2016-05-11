package dcos.metronome.api

import mesosphere.marathon.plugin.auth.{ Authenticator, AuthorizedAction, Authorizer, Identity }
import mesosphere.marathon.plugin.http.HttpRequest
import play.api.mvc._

import scala.concurrent.Future

/**
  * A request that adds the User for the current call
  */
case class AuthorizedRequest[A](identity: Identity, request: Request[A], authorizer: Authorizer) extends WrappedRequest(request) with Results {
  def withAuthorized[R](action: AuthorizedAction[R], resource: R)(block: R => Future[Result]): Future[Result] = {
    if (authorizer.isAuthorized(identity, action, resource)) block(resource)
    else Future.successful(Forbidden("Not authorized"))
  }
}

trait Authorization { self: Controller =>

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
      implicit val req = request
      authenticator.authenticate(new PluginRequest(request)).flatMap {
        case Some(identity) => block(AuthorizedRequest(identity, request, authorizer))
        case None           => handleNotAuthenticated
      }
    }

    private def handleNotAuthenticated(implicit request: RequestHeader): Future[Result] = {
      //logger.debug(s"Unauthenticated user trying to access '${request.uri}'")
      Future.successful(Unauthorized("Not authenticated"))
    }

    class PluginRequest(request: RequestHeader) extends HttpRequest {
      override def method: String = request.method
      override def requestPath: String = request.path
      override def remoteAddr: String = request.remoteAddress
      override def header(name: String): Seq[String] = request.headers.getAll(name)
      override def cookie(name: String): Option[String] = request.cookies.get(name).map(_.value)
      override def queryParam(name: String): Seq[String] = request.getQueryString(name).toSeq
    }

  }
}

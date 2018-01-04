package dcos.metronome
package api

import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.core.plugin.{ PluginDefinitions, PluginManager }
import mesosphere.marathon.plugin.auth.{ AuthorizedAction, Identity, Authorizer, Authenticator }
import mesosphere.marathon.plugin.http.{ HttpResponse, HttpRequest }
import play.api.http.Status

import scala.concurrent.Future
import scala.reflect.ClassTag

class TestAuthFixture extends Mockito with Status {

  type Auth = Authenticator with Authorizer

  var identity: Identity = new Identity {}

  var authenticated: Boolean = true
  var authorized: Boolean = true
  var authFn: Any => Boolean = { _ => true }

  def auth: Auth = new Authorizer with Authenticator {
    override def authenticate(request: HttpRequest): Future[Option[Identity]] = {
      Future.successful(if (authenticated) Some(identity) else None)
    }
    override def handleNotAuthenticated(request: HttpRequest, response: HttpResponse): Unit = {
      response.status(FORBIDDEN)
    }
    override def handleNotAuthorized(principal: Identity, response: HttpResponse): Unit = {
      response.status(UNAUTHORIZED)
    }
    override def isAuthorized[Resource](
      principal: Identity,
      action:    AuthorizedAction[Resource],
      resource:  Resource
    ): Boolean = {
      authFn(resource) && authorized
    }
  }

  val pluginManager = new PluginManager {
    override def definitions: PluginDefinitions = PluginDefinitions.None
    override def plugins[T](implicit ct: ClassTag[T]): Seq[T] = ct.runtimeClass.getSimpleName match {
      case "Authorizer"    => Seq(auth.asInstanceOf[T])
      case "Authenticator" => Seq(auth.asInstanceOf[T])
      case unknown         => Seq.empty[T]
    }
  }
}

package dcos.jobs.api.v1.controllers

import dcos.jobs.api.{ Authorization, YamlContent }
import dcos.jobs.greeting.{ Greeting, GreetingService }
import mesosphere.marathon.plugin.auth._
import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
import net.jcazevedo.moultingyaml._
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }
import play.twirl.api.Html
import dcos.jobs.api.v1.models._

class GreeterController(
    greetingService: GreetingService,
    val authenticator: Authenticator,
    val authorizer: Authorizer) extends Controller with YamlContent with Authorization {

  val greetingsList = Seq(
    Greeting(1, greetingService.greetingMessage("en"), "sameer"),
    Greeting(2, greetingService.greetingMessage("it"), "sam")
  )

  def greetings = Action { implicit request =>
    render {
      case AcceptJson() => Ok(Json.toJson(greetingsList))
      case AcceptYaml() => Ok(greetingsList.toYaml)
    }
  }

  def index = AuthorizedAction { implicit request =>
    Ok(Html(s"<h1>Welcome</h1><p>Your new application is ready.</p> ${request.identity}"))
  }

}

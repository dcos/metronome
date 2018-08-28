package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.{ AuthorizedController, RestController }
import dcos.metronome.api.v1.models.MetronomeInfoWrites
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.raml.MetricsConversion._
import mesosphere.marathon.raml.Raml
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.mvc.Action

class ApplicationController(metricsModule: MetricsModule) extends AuthorizedController {

  def ping = Action { Ok("pong") }

  def info = measuredAction {
    Action {
      Ok(MetronomeInfoWrites.writes(MetronomeInfoBuilder.metronomeInfo))
    }
  }

  def showMetrics = measuredAction {
    Action {
    val metricsJsonString = metricsModule.snapshot() match {
      case Left(kamonSnapshot)       => Json.stringify(Json.toJson(Raml.toRaml(kamonSnapshot)))
      case Right(dropwizardRegistry) => Json.stringify(Json.toJson(Raml.toRaml(dropwizardRegistry)))
    }
  }
}

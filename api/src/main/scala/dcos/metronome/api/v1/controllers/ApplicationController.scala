package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.RestController
import dcos.metronome.api.v1.models.MetronomeInfoWrites
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.raml.MetricsConversion._
import mesosphere.marathon.raml.Raml
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

class ApplicationController(cc: ControllerComponents, metricsModule: MetricsModule)
    extends RestController(cc) {

  def ping = Action { Ok("pong") }

  def info = Action {
    Ok(MetronomeInfoWrites.writes(MetronomeInfoBuilder.metronomeInfo))
  }

  def showMetrics = Action {
    val snapshot = Raml.toRaml(metricsModule.snapshot())
    val metricsJsonString = Json.stringify(Json.toJson(snapshot))
    Ok(metricsJsonString).as(ContentTypes.JSON)
  }
}

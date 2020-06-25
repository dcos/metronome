package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.{ ErrorDetail, RestController }
import dcos.metronome.api.v1.models.{ errorFormat, LeaderInfoWrites, MetronomeInfoWrites }
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.core.election.ElectionService
import mesosphere.marathon.raml.MetricsConversion._
import mesosphere.marathon.raml.Raml
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

class ApplicationController(cc: ControllerComponents, metricsModule: MetricsModule, electionService: ElectionService)
    extends RestController(cc) {

  def ping = Action { Ok("pong") }

  def info = Action {
    Ok(MetronomeInfoWrites.writes(MetronomeInfoBuilder.metronomeInfo))
  }

  def leader = Action {
    electionService.leaderHostPort match {
      case None         => NotFound(errorFormat.writes(ErrorDetail("There is no leader")))
      case Some(leader) => Ok(LeaderInfoWrites.writes(LeaderInfo(leader)))
    }
  }

  def showMetrics = Action {
    val metricsJsonString = metricsModule.snapshot() match {
      case Left(_) =>
        // Kamon snapshot
        throw new IllegalArgumentException("Only Dropwizard format is supported, cannot render metrics from Kamon snapshot. Make sure your metrics are configured correctly.")
      case Right(dropwizardRegistry) => Json.stringify(Json.toJson(Raml.toRaml(dropwizardRegistry)))
    }
    Ok(metricsJsonString).as(ContentTypes.JSON)
  }
}

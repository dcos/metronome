package dcos.metronome
package api.v1.controllers

import java.io.StringWriter
import java.util.concurrent.TimeUnit

import dcos.metronome.api.v1.models.MetronomeInfoWrites
import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.ObjectMapper
import dcos.metronome.MetronomeInfoBuilder
import dcos.metronome.api.RestController
import dcos.metronome.behavior.Metrics
import mesosphere.marathon.io.IO
import play.api.http.ContentTypes
import play.api.mvc.Action

class ApplicationController(metrics: Metrics) extends RestController {

  private[this] val metricsMapper = new ObjectMapper().registerModule(
    new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))

  def ping = Action { Ok("pong") }

  def info = Action {
    Ok(MetronomeInfoWrites.writes(MetronomeInfoBuilder.metronomeInfo))
  }

  def showMetrics = Action {
    val metricsJsonString = IO.using(new StringWriter()) { writer =>
      metricsMapper.writer().writeValue(writer, metrics.metricRegistry)
      writer.getBuffer.toString
    }
    Ok(metricsJsonString).as(ContentTypes.JSON)
  }
}

package dcos.metronome.api.v1.controllers

import akka.stream.scaladsl.Source
import dcos.metronome.ticking.TickingService
import play.api.mvc.{ Action, Controller }

class TickingController(tickingService: TickingService) extends Controller {
  def openStream = Action {
    val conf = tickingService.tickingConf
    val source = Source
      .tick(initialDelay = conf.initialDelay, interval = conf.interval, tick = conf.tick())
      .map { tick => tickingService.tickingOutput(tick) + "\n" }
      .limit(conf.backPressure.limit)

    Ok.chunked(source)
  }
}
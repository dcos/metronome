package dcos.metronome.ticking.impl

import dcos.metronome.ticking.{ TickingConf, TickingService }
import play.api.libs.json.Json

class TickingServiceImpl(val tickingConf: TickingConf) extends TickingService {
  override def tickingOutput(tick: Any): String = Json.obj("tick" -> tick.toString).toString()
}

package dcos.metronome.ticking

import dcos.metronome.ticking.impl.TickingServiceImpl

class TickingModule(tickingConf: TickingConf) {
  import com.softwaremill.macwire._

  lazy val tickingService: TickingService = wire[TickingServiceImpl]
}

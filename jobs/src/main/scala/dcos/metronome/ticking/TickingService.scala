package dcos.metronome.ticking

trait TickingService {
  def tickingConf: TickingConf
  def tickingOutput(tick: Any): String
}

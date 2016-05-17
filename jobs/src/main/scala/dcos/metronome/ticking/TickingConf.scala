package dcos.metronome.ticking

import scala.concurrent.duration.FiniteDuration

trait TickingConf {
  val initialDelay: FiniteDuration
  val interval: FiniteDuration
  def tick(): Any

  val backPressure: BackPressure
}

package dcos.metronome.jobrun

import scala.concurrent.duration.FiniteDuration

trait JobRunConfig {

  def askTimeout: FiniteDuration

}

package dcos.metronome
package jobspec

import scala.concurrent.duration.{ FiniteDuration, Duration }

trait JobSpecConfig {

  def askTimeout: FiniteDuration

}

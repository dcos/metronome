package dcos.metronome
package jobspec

import scala.concurrent.duration.{ FiniteDuration }

trait JobSpecConfig {

  def askTimeout: FiniteDuration

}

package dcos.metronome
package jobrun

import scala.concurrent.duration.FiniteDuration

trait JobRunConfig {

  def askTimeout: FiniteDuration

}

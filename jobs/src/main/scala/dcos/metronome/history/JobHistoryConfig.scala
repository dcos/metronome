package dcos.metronome
package history

import scala.concurrent.duration.FiniteDuration

trait JobHistoryConfig {

  def runHistoryCount: Int

  def askTimeout: FiniteDuration

}

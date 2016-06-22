package dcos.metronome.api

import scala.concurrent.duration.Duration

trait ApiConfig {

  def httpPort: Option[Int]
  def httpsPort: Int
  def hostname: String
  def leaderProxyTimeout: Duration

}

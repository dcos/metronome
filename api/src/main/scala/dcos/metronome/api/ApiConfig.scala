package dcos.metronome.api

import scala.concurrent.duration.Duration

trait ApiConfig {

  def disableHttp: Boolean
  def httpPort: Int
  def httpsPort: Int
  def hostname: String
  def leaderProxyTimeout: Duration

}

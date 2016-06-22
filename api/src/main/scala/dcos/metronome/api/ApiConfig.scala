package dcos.metronome.api

import scala.concurrent.duration.Duration

trait ApiConfig {

  def hostnameWithPort: String
  def leaderProxyTimeout: Duration

}

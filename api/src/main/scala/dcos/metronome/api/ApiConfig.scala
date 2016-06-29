package dcos.metronome.api

import scala.concurrent.duration.Duration

trait ApiConfig {

  def leaderProxyTimeout: Duration

  def hostname: String
  def effectivePort: Int
  def hostnameWithPort: String
}

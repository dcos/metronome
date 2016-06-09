package dcos.metronome.scheduler

import mesosphere.marathon.AllConf

import scala.concurrent.duration.FiniteDuration

trait SchedulerConfig {
  def scallopConf: AllConf
  def disableHttp: Boolean
  def httpPort: Int
  def httpsPort: Int
  def hostname: String
  def zkTimeoutDuration: FiniteDuration
  def mesosLeaderUiUrl: Option[String]
}

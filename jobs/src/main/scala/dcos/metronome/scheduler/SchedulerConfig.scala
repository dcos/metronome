package dcos.metronome.scheduler

import dcos.metronome.repository.impl.kv.ZkConfig
import mesosphere.marathon.AllConf

import scala.concurrent.duration.FiniteDuration

trait SchedulerConfig extends ZkConfig {
  def scallopConf: AllConf
  def leaderPreparationTimeout: FiniteDuration
  def disableHttp: Boolean
  def httpPort: Int
  def httpsPort: Int
  def hostname: String
  def zkTimeout: FiniteDuration
  def mesosLeaderUiUrl: Option[String]
  def reconciliationInterval: FiniteDuration
  def reconciliationTimeout: FiniteDuration
  def maxActorStartupTime: FiniteDuration
  def enableStoreCache: Boolean
  def mesosExecutorDefault: String
}

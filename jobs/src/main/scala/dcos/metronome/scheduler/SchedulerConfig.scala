package dcos.metronome
package scheduler

import dcos.metronome.repository.impl.kv.ZkConfig
import mesosphere.marathon.AllConf
import mesosphere.marathon.core.task.termination.TaskKillConfig

import scala.concurrent.duration.FiniteDuration

trait SchedulerConfig extends ZkConfig {
  def scallopConf: AllConf
  def taskKillConfig: TaskKillConfig
  def leaderPreparationTimeout: FiniteDuration
  def hostnameWithPort: String
  def zkTimeout: FiniteDuration
  def mesosLeaderUiUrl: Option[String]
  def reconciliationInterval: FiniteDuration
  def reconciliationTimeout: FiniteDuration
  def maxActorStartupTime: FiniteDuration
  def enableStoreCache: Boolean
  def mesosExecutorDefault: String
}

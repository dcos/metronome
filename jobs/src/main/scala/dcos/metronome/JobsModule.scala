package dcos.metronome

import akka.actor.ActorSystem
import dcos.metronome.history.JobHistoryModule
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import dcos.metronome.behavior.BehaviorModule
import dcos.metronome.jobinfo.JobInfoModule
import dcos.metronome.jobrun.JobRunModule
import dcos.metronome.jobspec.JobSpecModule
import dcos.metronome.repository.RepositoryModule
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }

class JobsModule(
    config:              JobsConfig,
    actorSystem:         ActorSystem,
    clock:               Clock,
    metricsRegistry:     MetricRegistry,
    healthCheckRegistry: HealthCheckRegistry
) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  def pluginManger: PluginManager = pluginModule.pluginManager

  lazy val behaviorModule = new BehaviorModule(config, metricsRegistry, healthCheckRegistry)

  lazy val repositoryModule = new RepositoryModule()

  lazy val jobRunModule = new JobRunModule(config, actorSystem, clock, repositoryModule.jobRunRepository, behaviorModule.behavior)

  lazy val jobSpecModule = new JobSpecModule(config, actorSystem, clock, repositoryModule.jobSpecRepository, jobRunModule.jobRunService, behaviorModule.behavior)

  lazy val jobHistoryModule = new JobHistoryModule(config, actorSystem, clock, repositoryModule.jobHistoryRepository, behaviorModule.behavior)

  lazy val jobInfoModule = new JobInfoModule(jobSpecModule.jobSpecService, jobRunModule.jobRunService, behaviorModule.behavior)
}


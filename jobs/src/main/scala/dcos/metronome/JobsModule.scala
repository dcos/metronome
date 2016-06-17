package dcos.metronome

import akka.actor.ActorSystem
import dcos.metronome.history.JobHistoryModule
import dcos.metronome.behavior.BehaviorModule
import dcos.metronome.jobinfo.JobInfoModule
import dcos.metronome.jobrun.JobRunModule
import dcos.metronome.jobspec.JobSpecModule
import dcos.metronome.repository.{ RepositoryModule, SchedulerRepositoriesModule }
import dcos.metronome.scheduler.SchedulerModule
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }

class JobsModule(
    config:      JobsConfig,
    actorSystem: ActorSystem,
    clock:       Clock
) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  def pluginManger: PluginManager = pluginModule.pluginManager

  lazy val behaviorModule = new BehaviorModule(config, schedulerModule.metricsModule.metricsRegistry, schedulerModule.metricsModule.healthCheckRegistry)

  lazy val repositoryModule = new RepositoryModule(config)

  lazy val schedulerRepositoriesModule = new SchedulerRepositoriesModule(config, repositoryModule)

  lazy val schedulerModule: SchedulerModule = new SchedulerModule(config, actorSystem, clock, schedulerRepositoriesModule)

  lazy val jobRunModule = {
    val launchQueue = schedulerModule.launchQueueModule.launchQueue
    val driverHolder = schedulerModule.schedulerDriverHolder
    new JobRunModule(config, actorSystem, clock, repositoryModule.jobRunRepository, launchQueue, driverHolder, behaviorModule.behavior, schedulerModule.leadershipModule)
  }

  lazy val jobSpecModule = new JobSpecModule(
    config,
    actorSystem,
    clock,
    repositoryModule.jobSpecRepository,
    jobRunModule.jobRunService,
    behaviorModule.behavior,
    schedulerModule.leadershipModule
  )

  lazy val jobHistoryModule = new JobHistoryModule(config, actorSystem, clock, repositoryModule.jobHistoryRepository, behaviorModule.behavior)

  lazy val jobInfoModule = new JobInfoModule(jobSpecModule.jobSpecService, jobRunModule.jobRunService, behaviorModule.behavior, jobHistoryModule.jobHistoryService)
}


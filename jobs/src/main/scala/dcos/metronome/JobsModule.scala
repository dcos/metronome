package dcos.metronome

import java.time.Clock

import akka.actor.ActorSystem
import dcos.metronome.history.JobHistoryModule
import dcos.metronome.behavior.BehaviorModule
import dcos.metronome.jobinfo.JobInfoModule
import dcos.metronome.jobrun.JobRunModule
import dcos.metronome.jobspec.JobSpecModule
import dcos.metronome.queue.LaunchQueueModule
import dcos.metronome.repository.{ RepositoryModule, SchedulerRepositoriesModule }
import dcos.metronome.scheduler.SchedulerModule
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }

class JobsModule(
  config:      JobsConfig,
  actorSystem: ActorSystem,
  clock:       Clock) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  def pluginManger: PluginManager = pluginModule.pluginManager

  private[this] lazy val metricsModule = new MetricsModule()

  lazy val behaviorModule = new BehaviorModule(config, metricsModule.metricsRegistry, metricsModule.healthCheckRegistry)

  lazy val repositoryModule = new RepositoryModule(config)

  lazy val schedulerRepositoriesModule = new SchedulerRepositoriesModule(config, repositoryModule, metricsModule)

  lazy val schedulerModule: SchedulerModule = new SchedulerModule(
    config,
    actorSystem,
    clock,
    schedulerRepositoriesModule,
    pluginModule,
    metricsModule)

  lazy val jobRunModule = {
    val launchQueue = schedulerModule.launchQueueModule.launchQueue
    val taskTracker = schedulerModule.taskTrackerModule.taskTracker
    val driverHolder = schedulerModule.schedulerDriverHolder
    new JobRunModule(config, actorSystem, clock, repositoryModule.jobRunRepository, launchQueue, taskTracker, driverHolder, behaviorModule.behavior, schedulerModule.leadershipModule)
  }

  lazy val jobSpecModule = new JobSpecModule(
    config,
    actorSystem,
    clock,
    repositoryModule.jobSpecRepository,
    jobRunModule.jobRunService,
    behaviorModule.behavior,
    schedulerModule.leadershipModule)

  lazy val jobHistoryModule = new JobHistoryModule(
    config,
    actorSystem,
    clock,
    repositoryModule.jobHistoryRepository,
    behaviorModule.behavior,
    schedulerModule.leadershipModule)

  lazy val jobInfoModule = new JobInfoModule(
    jobSpecModule.jobSpecService,
    jobRunModule.jobRunService,
    behaviorModule.behavior,
    jobHistoryModule.jobHistoryService)

  lazy val queueModule = new LaunchQueueModule(schedulerModule.launchQueueModule.launchQueue)
}


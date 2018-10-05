package dcos.metronome

import java.time.Clock

import akka.actor.ActorSystem
import dcos.metronome.history.JobHistoryModule
import dcos.metronome.jobinfo.JobInfoModule
import dcos.metronome.jobrun.JobRunModule
import dcos.metronome.jobspec.JobSpecModule
import dcos.metronome.queue.LaunchQueueModule
import dcos.metronome.repository.{ RepositoryModule, SchedulerRepositoriesModule }
import dcos.metronome.scheduler.SchedulerModule
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.core.base.{ ActorsModule, JvmExitsCrashStrategy, LifecycleState }
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }

class JobsModule(
  config:        JobsConfig,
  actorSystem:   ActorSystem,
  clock:         Clock,
  metricsModule: MetricsModule) {

  private[this] lazy val crashStrategy = JvmExitsCrashStrategy
  private[this] lazy val lifecycleState = LifecycleState.WatchingJVM
  private[this] lazy val pluginModule = new PluginModule(config.scallopConf, crashStrategy)
  def pluginManger: PluginManager = pluginModule.pluginManager

  lazy val repositoryModule = new RepositoryModule(config)

  lazy val actorsModule = new ActorsModule(actorSystem)

  lazy val schedulerRepositoriesModule = new SchedulerRepositoriesModule(metricsModule.metrics, config, repositoryModule, lifecycleState, actorsModule, actorSystem)

  val schedulerModule: SchedulerModule = new SchedulerModule(
    metricsModule.metrics,
    config,
    actorSystem,
    clock,
    schedulerRepositoriesModule,
    pluginModule,
    lifecycleState,
    crashStrategy,
    actorsModule)

  val jobRunModule = {
    val launchQueue = schedulerModule.launchQueueModule.launchQueue
    val instanceTracker = schedulerModule.instanceTrackerModule.instanceTracker
    val driverHolder = schedulerModule.schedulerDriverHolder
    new JobRunModule(config, actorSystem, clock, repositoryModule.jobRunRepository, launchQueue, instanceTracker, driverHolder, metricsModule.metrics, schedulerModule.leadershipModule)
  }

  val jobSpecModule = new JobSpecModule(
    config,
    actorSystem,
    clock,
    repositoryModule.jobSpecRepository,
    jobRunModule.jobRunService,
    metricsModule.metrics,
    schedulerModule.leadershipModule)

  val jobHistoryModule = new JobHistoryModule(
    config,
    actorSystem,
    clock,
    repositoryModule.jobHistoryRepository,
    metricsModule.metrics,
    schedulerModule.leadershipModule)

  val jobInfoModule = new JobInfoModule(
    jobSpecModule.jobSpecService,
    jobRunModule.jobRunService,
    jobHistoryModule.jobHistoryService)

  val queueModule = new LaunchQueueModule(schedulerModule.launchQueueModule.launchQueue)
}


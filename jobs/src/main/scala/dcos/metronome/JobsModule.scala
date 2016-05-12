package dcos.metronome

import akka.actor.ActorSystem
import dcos.metronome.history.JobHistoryModule
import dcos.metronome.jobrun.JobRunModule
import dcos.metronome.jobspec.JobSpecModule
import dcos.metronome.repository.RepositoryModule
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }

class JobsModule(config: JobsConfig, actorSystem: ActorSystem, clock: Clock, launchQueue: LaunchQueue) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  def pluginManger: PluginManager = pluginModule.pluginManager

  lazy val repositoryModule = new RepositoryModule()

  lazy val jobRunModule = new JobRunModule(config, actorSystem, clock, repositoryModule.jobRunRepository, launchQueue)

  lazy val jobSpecModule = new JobSpecModule(config, actorSystem, clock, repositoryModule.jobSpecRepository, jobRunModule.jobRunService)

  lazy val jobHistoryModule = new JobHistoryModule(config, actorSystem, clock, repositoryModule.jobHistoryRepository)

}


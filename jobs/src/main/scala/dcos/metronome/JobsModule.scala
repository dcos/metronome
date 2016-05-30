package dcos.metronome

import akka.actor.ActorSystem
import dcos.metronome.jobspec.JobSpecModule
import dcos.metronome.repository.RepositoryModule
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }

class JobsModule(config: JobsConfig, actorSystem: ActorSystem, clock: Clock) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  def pluginManger: PluginManager = pluginModule.pluginManager

  lazy val repositoryModule = new RepositoryModule()

  lazy val jobSpecModule = new JobSpecModule(config, actorSystem, clock, repositoryModule.jobSpecRepository)
  def jobSpecService = jobSpecModule.jobSpecService

}


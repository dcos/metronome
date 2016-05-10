package dcos.jobs

import dcos.jobs.greeting.{ GreetingService, GreetingModule }
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }
import com.softwaremill.macwire._

class JobsModule(config: JobsConfig) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  lazy val pluginManger: PluginManager = pluginModule.pluginManager

  private[this] lazy val greetingModule: GreetingModule = wire[GreetingModule]
  lazy val greetingService: GreetingService = greetingModule.greetingService
}


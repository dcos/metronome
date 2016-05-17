package dcos.metronome

import dcos.metronome.greeting.{ GreetingModule, GreetingService }
import mesosphere.marathon.core.plugin.{ PluginManager, PluginModule }
import com.softwaremill.macwire._
import dcos.metronome.ticking.{ TickingModule, TickingService }

class JobsModule(config: JobsConfig) {

  private[this] lazy val pluginModule = new PluginModule(config.scallopConf)
  lazy val pluginManger: PluginManager = pluginModule.pluginManager

  private[this] lazy val greetingModule: GreetingModule = wire[GreetingModule]
  lazy val greetingService: GreetingService = greetingModule.greetingService

  private[this] lazy val tickingModule: TickingModule = wire[TickingModule]
  lazy val tickingService: TickingService = tickingModule.tickingService
}


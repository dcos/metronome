package dcos.metronome.greeting

import dcos.metronome.greeting.impl.GreetingServiceImpl

class GreetingModule(config: GreetingConf) {

  import com.softwaremill.macwire._

  lazy val greetingService: GreetingService = wire[GreetingServiceImpl]
}

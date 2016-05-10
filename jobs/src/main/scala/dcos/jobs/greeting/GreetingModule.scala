package dcos.jobs.greeting

import dcos.jobs.greeting.impl.GreetingServiceImpl

class GreetingModule(config: GreetingConf) {

  import com.softwaremill.macwire._

  lazy val greetingService: GreetingService = wire[GreetingServiceImpl]
}

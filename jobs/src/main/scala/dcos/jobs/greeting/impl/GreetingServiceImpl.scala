package dcos.jobs.greeting.impl

import dcos.jobs.greeting.{ GreetingConf, GreetingService }

class GreetingServiceImpl(conf: GreetingConf) extends GreetingService {

  override def greetingMessage(language: String): String = language match {
    case "it" => "Messi"
    case _    => "Hello"
  }

}

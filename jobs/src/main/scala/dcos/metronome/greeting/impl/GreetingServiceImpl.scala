package dcos.metronome.greeting.impl

import dcos.metronome.greeting.{ GreetingConf, GreetingService }

class GreetingServiceImpl(conf: GreetingConf) extends GreetingService {

  override def greetingMessage(language: String): String = language match {
    case "it" => "Messi"
    case _    => "Hello"
  }

}

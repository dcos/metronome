package dcos.metronome.greeting

trait GreetingService {

  def greetingMessage(language: String): String
}

package dcos.jobs.greeting

trait GreetingService {

  def greetingMessage(language: String): String
}

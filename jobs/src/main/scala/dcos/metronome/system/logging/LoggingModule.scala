package dcos.metronome.system.logging

import akka.actor.ActorSystem
import dcos.metronome.system.logging.impl.{ LoggingServiceDelegate, LoggingServiceActor }

/**
  * Created by stas on 10.06.16.
  */
class LoggingModule(actorSystem: ActorSystem) {

  val loggingActor = actorSystem.actorOf(LoggingServiceActor.props())

  def loggingService = new LoggingServiceDelegate(loggingActor)

}

package dcos.metronome.api.v1.controllers

import akka.actor.{ ActorSystem, Props }
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import dcos.metronome.model.Event
import dcos.metronome.model.Event._
import play.api.libs.EventSource
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }

import scala.concurrent.ExecutionContext

class EventStreamController(actorSystem: ActorSystem) extends Controller {
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  /**
    * Forwards messages from event stream to an akka stream.
    * Later on, a sink is connected through the chunked method. Message are forwarded to that sink.
    */
  class EventPublisherActor extends ActorPublisher[Event] {
    override def preStart = actorSystem.eventStream.subscribe(self, classOf[Event])
    override def receive = {
      // JobSpec
      case event: JobSpecCreated if isActive && totalDemand > 0 => onNext(event)
      case event: JobSpecUpdated if isActive && totalDemand > 0 => onNext(event)
      case event: JobSpecDeleted if isActive && totalDemand > 0 => onNext(event)
      // JobRun
      case event: JobRunStarted if isActive && totalDemand > 0  => onNext(event)
      case event: JobRunUpdate if isActive && totalDemand > 0   => onNext(event)
      case event: JobRunFinished if isActive && totalDemand > 0 => onNext(event)
      case event: JobRunFailed if isActive && totalDemand > 0   => onNext(event)
    }
  }

  // Source based on a publishing actor who forwards messages from the event stream.
  val dataSource = Source.actorPublisher[Event](Props(new EventPublisherActor()))

  /**
    * Route in order to provide an SSE channel.
    * @return
    */
  def connect = Action {
    Ok.chunked(dataSource.map { event =>
      Json.toJson(event).toString()
    }.via(EventSource.flow))
  }
}

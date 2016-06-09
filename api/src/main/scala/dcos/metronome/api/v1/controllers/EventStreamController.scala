package dcos.metronome.api.v1.controllers

import akka.actor.{ActorSystem, Props}
import akka.stream.OverflowStrategy
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import dcos.metronome.model.Event
import dcos.metronome.api.v1.models._
import play.api.libs.EventSource
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

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
      case event: Event => if (isActive && totalDemand > 0) {
        println(Json.toJson(event)(eventWrites).toString())
        onNext(event)
      } else
        // TODO: log the message properly
        println(s"Client has no demand. Event ${event.eventType} is being dropped.")
    }
  }

  // Source based on a publishing actor who forwards messages from the event stream.
  val dataSource = Source.actorPublisher[Event](Props(new EventPublisherActor())).buffer(10, OverflowStrategy.dropNew)

  /**
    * Route in order to provide an SSE channel.
    * @return
    */
  def connect = Action {
    Ok.chunked(dataSource.map { event =>
      Json.toJson(event)(eventWrites).toString()
    }.via(EventSource.flow))
  }
}

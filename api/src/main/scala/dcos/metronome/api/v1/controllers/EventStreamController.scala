package dcos.metronome.api.v1.controllers

import akka.actor.{ ActorSystem, Props }
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import org.joda.time.DateTime
import play.api.libs.EventSource
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }
import play.twirl.api.Html

import scala.concurrent.ExecutionContext

case class TestMessage(msg: String)

class EventStreamController(actorSystem: ActorSystem) extends Controller {
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  /**
    * Forwards messages from event stream to an akka stream.
    * Later on, a sink is connected through the chunked method. Message are forwarded to that sink.
    */
  class EventPublisherActor extends ActorPublisher[TestMessage] {
    override def preStart = actorSystem.eventStream.subscribe(self, classOf[TestMessage])

    override def receive = {
      case element: TestMessage if isActive && totalDemand > 0 => onNext(element)
    }
  }

  // Source based on a publishing actor who forwards messages from the event stream.
  val dataSource = Source.actorPublisher[TestMessage](Props(new EventPublisherActor()))

  /**
    * Route in order to provide an SSE channel.
    * @return
    */
  def connect = Action {
    Ok.chunked(dataSource.map { testMessage =>
      Json.obj("testMessage" -> testMessage.msg).toString
    }.via(EventSource.flow))
  }

  /**
    * Route in order to publish a message to event stream.
    * @return
    */
  def postMessage = Action {
    val msg = DateTime.now.toString("hh:mm:ss")
    actorSystem.eventStream.publish(TestMessage(msg))
    Ok(Html(s"Published message to event stream: $msg"))
  }
}

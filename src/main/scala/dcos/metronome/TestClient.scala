package dcos.metronome

import akka.Done
import akka.actor._
import akka.stream._
import akka.stream.scaladsl.{ Flow, Sink }
import akka.util.ByteString
import play.api.libs.json.Json
import play.api.libs.ws.ahc._

import scala.concurrent.Future

object TestClient extends App {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val ws = AhcWSClient()

  /*
  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = {
    Http().outgoingConnection("localhost", port = 5050)
  }

  def request[T](call: Call, sink: Sink[HttpResponse, T]): T = {
    val body = HttpEntity.apply(protobufContent, call.toByteArray)
    Source.single(HttpRequest(uri = "/api/v1/scheduler", method = POST, headers = header, entity = body))
      .via(connectionFlow)
      .runWith(sink)
  }

  val printResponseSink = Sink.foreach[HttpResponse] { response =>
    val printer = Sink.foreach[ByteString] { byteString =>
      println(byteString.utf8String)
    }
    println(s"Got response $response")
    response.entity.dataBytes.runWith(printer)
  }

  def subscribe(): Future[Done] = {
    val responseSink = Sink.foreach[HttpResponse] { response =>
      response.entity.dataBytes.map(toEvent).runWith(printResponseSink)
    }
    request(call, responseSink)
  }
*/

  val eventStreamResponse = ws.url("http://127.0.0.1:9000/events").stream()

  val json =
    """
      |{
      |  "id": "/a",
      |  "description": "Marathon DB cleaner foo",
      |  "labels": {
      |    "team": "dev-agility",
      |    "project": "marathon",
      |    "stage": "production"
      |  },
      |  "schedule": {
      |    "id": "every 3 minute",
      |    "cron": "* * * * *",
      |    "timezone": "America/Chicago",
      |    "startingDeadlineSeconds" : 5,
      |    "concurrencyPolicy": "allow",
      |    "enabled": true
      |  },
      |  "run": {
      |    "cpus": 1,
      |    "mem": 1,
      |    "disk": 1,
      |    "artifacts": [
      |      {
      |        "url": "https://foo.com/archive.zip",
      |        "executable": false,
      |        "extract": true,
      |        "cache": true
      |      }
      |    ],
      |    "placement": {
      |      "constraints": [
      |        {
      |          "attr": "hostname",
      |          "op": "LIKE",
      |          "value": ".*PROD.*"
      |        }
      |      ]
      |    },
      |    "cmd": "/opt/mesosphere/cleaner",
      |    "args": [],
      |    "user": "marathon",
      |    "env": {
      |      "DRY_RUN": "false",
      |      "ZK": "zk://master.mesos:2181/marathon"
      |    },
      |    "volumes": [
      |      {
      |        "containerPath": "/logs",
      |        "hostPath": "/var/log/mesosphere/marathon-cleaner",
      |        "mode": "RW"
      |      }
      |    ],
      |    "restart": {
      |      "restart": "never",
      |      "activeDeadlineSeconds": 120
      |    }
      |  }
      |}
      |
    """.stripMargin

  val data = Json.parse(json)

  val bytesReturned: Future[Done] = eventStreamResponse.flatMap {
    res =>
      val sink = Sink.foreach[ByteString] { bytes =>
        Thread.sleep(500000)
        println(bytes.toArray)
      }

      // materialize and run the stream
      res.body.runWith(sink).andThen {
        case result =>
          // Close the output stream whether there was an error or not

          // Get the result or rethrow the error
          result.get
      }
  }

  val futureResponse = ws.url("http://127.0.0.1:9000/jobs").post(data)
  futureResponse.onComplete(res =>
    println(res.get.body))
}
package dcos.metronome.api

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import dcos.metronome.model.JobSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Second, Span}
import org.scalatestplus.play._
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Results
import play.api.test.Helpers._
import dcos.metronome.api.v1.models._
import play.api.libs.json.Json

import scala.concurrent.Future

class ServerSpec extends PlaySpec
    with OneServerPerSuiteWithComponents[MockApiComponents with AhcWSComponents]
    with Results
    with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second), Span(50, Millis))
  override def createComponents(context: Context) = new MockApiComponents(context) with AhcWSComponents

  "Server query should" should {
    "work" in {
      implicit val ec = app.materializer.executionContext
      val wsClient = components.wsClient

      whenReady(wsUrl("/ping")(portNumber, wsClient).get) { response =>
        response.status mustBe OK
        response.body mustBe "pong"
      }
    }
  }
}


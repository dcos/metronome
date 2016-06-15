package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ MockApiComponents, OneAppPerTestWithComponents }
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json.{ JsString, JsDefined }
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ApplicationControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] {

  "ping" should {
    "send a pong" in {
      val ping = route(app, FakeRequest(GET, "/ping")).get
      status(ping) mustBe OK
      contentType(ping) mustBe Some("text/plain")
      contentAsString(ping) must include("pong")
    }
  }

  "metrics" should {
    "give metrics as json" in {
      val metrics = route(app, FakeRequest(GET, "/v1/metrics")).get
      status(metrics) mustBe OK
      contentType(metrics) mustBe Some("application/json")
      contentAsJson(metrics) \ "version" mustBe JsDefined(JsString("3.0.0"))
    }
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}

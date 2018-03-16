package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.{ MockApiComponents, OneAppPerTestWithComponents }
import org.scalatestplus.play.PlaySpec
import org.scalatest.Matchers._
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
    }
  }

  "info" should {
    "send verison info" in {
      val info = route(app, FakeRequest(GET, "/info")).get
      status(info) mustBe OK
      contentType(info) mustBe Some("application/json")
      (contentAsJson(info) \ "version").as[String] should include regex "\\d+.\\d+.\\d+".r
      (contentAsJson(info) \ "libVersion").as[String] should include regex "\\d+.\\d+.\\d+".r
    }
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}

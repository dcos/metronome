package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.{ MockApiComponents, OneAppPerTestWithComponents }
import mesosphere.marathon.core.election.ElectionService
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import org.scalatest.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import play.api.ApplicationLoader.Context
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ApplicationControllerTest extends PlaySpec
    with OneAppPerTestWithComponents[MockApiComponents]
    with MockitoSugar {

  val electionServiceMock = mock[ElectionService]

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
    "send version info" in {
      val info = route(app, FakeRequest(GET, "/info")).get
      status(info) mustBe OK
      contentType(info) mustBe Some("application/json")
      (contentAsJson(info) \ "version").as[String] should include regex "\\d+.\\d+.\\d+".r
      (contentAsJson(info) \ "libVersion").as[String] should include regex "\\d+.\\d+.\\d+".r
    }
  }

  "leader" should {
    "send leader info" in {
      when(electionServiceMock.leaderHostPort).thenReturn(Some("localhost:8080"))
      val info = route(app, FakeRequest(GET, "/leader")).get
      status(info) mustBe OK
      contentType(info) mustBe Some("application/json")
      (contentAsJson(info) \ "leader").as[String] should be("localhost:8080")
    }

    "send not found" in {
      when(electionServiceMock.leaderHostPort).thenReturn(None)
      val info = route(app, FakeRequest(GET, "/leader")).get
      status(info) mustBe NOT_FOUND
      contentType(info) mustBe Some("application/json")
      (contentAsJson(info) \ "message").as[String] should be("There is no leader")
    }
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context) {
    override lazy val electionService = electionServiceMock
  }
}

package dcos.metronome
package api

import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.test.Helpers._
import play.api.test._

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class ApplicationSpec extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] {

  override def createComponents(context: Context) = new MockApiComponents(context)

  "Routes" should {
    "send 404 on a bad request" in {
      route(app, FakeRequest(GET, "/boum")).map(status) mustBe Some(NOT_FOUND)
    }
  }

  "HomeController" should {
    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/ping")).get
      status(home) mustBe OK
      contentType(home) mustBe Some("text/plain")
      contentAsString(home) must include("pong")
    }
  }
}

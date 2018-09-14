package dcos.metronome

import com.typesafe.config.ConfigFactory
import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }
import play.api.Configuration

class MetronomeConfigTest extends FunSuite with Matchers with GivenWhenThen {
  private def fromConfig(cfg: String): MetronomeConfig =
    new MetronomeConfig(new Configuration(ConfigFactory.parseString(cfg)))

  test("Http and Https ports with valid parseable strings") {
    Given("http Port is a valid port string")
    val httpPort = "9000"
    val httpsPort = "9010"

    When("Config parser tries to extract it")
    val cfg = fromConfig(
      s"""
         | play.server.http.port="$httpPort"
         | play.server.https.port="$httpsPort"
       """.stripMargin)

    Then("Should return an integer of that given port")
    cfg.httpPort shouldEqual Some(9000)
    cfg.httpsPort shouldEqual 9010
  }

  test("Http overriden with `disabled`") {
    Given("http Port is `disabled`")
    val httpPort = "disabled"
    val httpsPort = "9010"

    When("Config parser tries to extract it")
    val cfg = fromConfig(
      s"""
         | play.server.http.port="$httpPort"
         | play.server.https.port="$httpsPort"
       """.stripMargin)

    Then("Http port should be None")
    cfg.httpPort shouldEqual None

    Then("Effective port should be https")
    cfg.effectivePort shouldEqual 9010
  }
}

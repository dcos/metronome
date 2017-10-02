package dcos.metronome.api.v1

import dcos.metronome.api.v1.models._
import dcos.metronome.model.CronSpec
import dcos.metronome.utils.test.Mockito
import org.scalatest.{ FunSuite, Matchers }
import play.api.libs.json.Json

class CronSpecFormatTest extends FunSuite with Mockito with Matchers {

  test("Every minute") {
    val cronString = "* * * * *"
    CronSpec.isValid(cronString) shouldBe true

    val spec = CronSpec(cronString)
    spec.toString shouldEqual cronString

    Json.toJson(spec).as[CronSpec] shouldEqual spec
  }

  test("Every 2 minutes") {
    val cronString = "*/2 * * * *"
    CronSpec.isValid(cronString) shouldBe true

    val spec = CronSpec(cronString)
    spec.toString shouldEqual cronString

    Json.toJson(spec).as[CronSpec] shouldEqual spec
  }

  test("Every 5 minutes") {
    val cronString = "*/5 * * * *"
    CronSpec.isValid(cronString) shouldBe true

    val spec = CronSpec(cronString)
    spec.toString shouldEqual cronString

    Json.toJson(spec).as[CronSpec] shouldEqual spec
  }

  test("Every Friday") {
    val cronString = "0 0 * * 5"
    CronSpec.isValid(cronString) shouldBe true

    val spec = CronSpec(cronString)
    spec.toString shouldEqual cronString

    Json.toJson(spec).as[CronSpec] shouldEqual spec
  }
}

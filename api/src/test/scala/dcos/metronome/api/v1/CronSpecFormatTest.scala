package dcos.metronome.api.v1

import dcos.metronome.api.v1.models._
import dcos.metronome.model.CronSpec
import dcos.metronome.utils.test.Mockito
import org.joda.time.DateTime
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

  test("Every minute alternate") {
    val redundantCronString = "*/1 * * * *"
    val correctCronString = "* * * * *"
    CronSpec.isValid(redundantCronString) shouldBe true
    CronSpec.isValid(correctCronString) shouldBe true

    val spec = CronSpec(redundantCronString)
    // the */1 is actually redundant and will be 'fixed' by the parser
    spec.toString shouldEqual correctCronString

    Json.toJson(spec).as[CronSpec].toString shouldEqual correctCronString
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

  test("First Monday Of The Month") {
    val cronString = "0 9 1-7 * 1"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 11, 6, 9, 0)
  }

  test("Each weekday the first week Of The Month") {
    val cronString = "0 9 1-7 * 1-5"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 10, 3, 9, 0)
  }

  test("Each weekend day the first week Of The Month") {
    val cronString = "0 9 1-7 * 6-7"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 10, 7, 9, 0)
  }

  test("Each weekday the second week Of The Month") {
    val cronString = "0 9 8-14 * 1-5"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 10, 9, 9, 0)
  }

  test("Each weekend day the fourth week Of The Month") {
    val cronString = "0 9 22-28 * 6-7"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 10, 22, 9, 0)
  }

  test("Each day on a wednesday") {
    val cronString = "* * * * 3"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 10, 4, 0, 0)
  }

  test("Each day slash of 1 on a wednesday") {
    val cronString = "* * */1 * 3"

    val spec = CronSpec(cronString)
    val currentDateTime: DateTime = new DateTime(2017, 10, 2, 10, 0)

    val nextCronDate = spec.nextExecution(currentDateTime)

    nextCronDate shouldEqual new DateTime(2017, 10, 4, 0, 0)
  }

  test("A cron job should execute next Monday when day of week is 1") {
    val date = (year: Int, month: Int, day: Int) => new DateTime(year, month, day, 0, 0)

    // With cronutils 4.1.0, the execution date is sometimes wrong if the
    // intended date falls on the first or last day of the month
    val expectations = Map(
      date(2017, 4, 25) -> date(2017, 5, 1),
      date(2017, 7, 28) -> date(2017, 7, 31),
      date(2017, 12, 27) -> date(2018, 1, 1),
      date(2018, 4, 29) -> date(2018, 4, 30),
      date(2018, 9, 30) -> date(2018, 10, 1),
      date(2018, 12, 26) -> date(2018, 12, 31)
    )
    for ((fromDate, executionDate) <- expectations) {
      CronSpec.apply("* * * * 1").nextExecution(fromDate) shouldEqual executionDate
    }
  }
}

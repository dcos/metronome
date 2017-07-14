package dcos.metronome.api.v1

import dcos.metronome.model.CronSpec
import org.joda.time.DateTime
import org.scalatest.{ FunSuite, Matchers }

class CronSpecExecutionTest extends FunSuite with Matchers {

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
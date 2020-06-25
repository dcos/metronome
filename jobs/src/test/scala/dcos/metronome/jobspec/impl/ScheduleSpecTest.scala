package dcos.metronome.jobspec.impl

import java.time.{Instant, ZoneId}

import dcos.metronome.model.{ConcurrencyPolicy, CronSpec, ScheduleSpec}
import dcos.metronome.utils.test.Mockito
import org.scalatest.{FunSuite, GivenWhenThen, Matchers}

import scala.concurrent.duration._

/**
  * This tests an issue which was present in Metronome 0.4 and was already fixed in newer versions.
  * It originally came up in https://jira.mesosphere.com/browse/DCOS_OSS-2509 and then again in
  * https://jira.mesosphere.com/browse/COPS-4788.
  *
  * It manifested in `schedule.nextExecution(from)` returning a date in the past, e.g.:
  * 2019-03-30 23:54:59: [info] d.m.j.i.JobSpecSchedulerActor - Start next run of job restart-something, which was scheduled for Some(2019-03-30T22:55:00.000Z)
  * 2019-03-30 23:54:59: [info] d.m.j.i.JobSpecSchedulerActor - Spec restart-something: next run is scheduled for: 2019-03-01T22:55:00.000Z (in 60 seconds)
  * Which would then result in a job run being scheduled at each following timestamp according to the cron, all over again.
  *
  * This test is based on this given config and context: log time, last execution, and schedule spec.
  */
class ScheduleSpecTest extends FunSuite with Matchers with Mockito with GivenWhenThen {
  test("nextExecution when close to daylight savings") {
    val schedule = ScheduleSpec(
      id = "default",
      cron = CronSpec("55 23 * * *"),
      timeZone = ZoneId.of("Europe/Rome"),
      startingDeadline = 900.seconds,
      concurrencyPolicy = ConcurrencyPolicy.Allow,
      enabled = true
    )

    Given("a schedule that was last run at 22:55")
    val lastScheduledAt = Instant.parse("2019-03-30T22:55:00.000Z")

    When("we are now close to midnight and compute the next scheduled time")
    val now = Instant.parse("2019-03-30T23:54:59.000Z")
    val nextTime = schedule.nextExecution(lastScheduledAt)
    // 60 secs is the smallest unit of reschedule time for cron
    val inSeconds = Math.max(java.time.Duration.between(now, nextTime).getSeconds, 60)
    println(s"now is $now, nextScheduleIn = $inSeconds seconds, next run is scheduled for: $nextTime")

    Then("The next run should be scheduled on the 31st")
    val expected = Instant.parse("2019-03-31T21:55:00Z")
    nextTime shouldEqual expected
  }
}

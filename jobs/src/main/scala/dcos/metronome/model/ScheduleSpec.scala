package dcos.metronome
package model

import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

import com.wix.accord.Validator
import com.wix.accord.dsl._

import scala.concurrent.duration._

case class ScheduleSpec(
    id: String,
    cron: CronSpec,
    timeZone: ZoneId = ScheduleSpec.DefaultTimeZone,
    startingDeadline: Duration = ScheduleSpec.DefaultStartingDeadline,
    concurrencyPolicy: ConcurrencyPolicy = ScheduleSpec.DefaultConcurrencyPolicy,
    enabled: Boolean = ScheduleSpec.DefaultEnabled
) {
  def clock: Clock = ScheduleSpec.DefaultClock

  def nextExecution(after: Instant): Instant = {
    val localAfter = ZonedDateTime.ofInstant(after, timeZone)
    val localNext = cron.nextExecution(localAfter)
    localNext.toInstant
  }

  def nextExecution(): Instant = nextExecution(clock.instant())
}

object ScheduleSpec {
  val DefaultTimeZone = ZoneId.of("UTC")
  val DefaultStartingDeadline = 15.minutes
  val DefaultConcurrencyPolicy = ConcurrencyPolicy.Allow
  val DefaultEnabled = true
  val DefaultClock = Clock.systemUTC()

  implicit lazy val validScheduleSpec: Validator[ScheduleSpec] = validator[ScheduleSpec] { spec =>
    spec.startingDeadline >= 1.minute
  }
}

package dcos.metronome
package model

import java.time.Clock

import com.wix.accord.Validator
import com.wix.accord.dsl._
import org.joda.time.{ DateTime, DateTimeZone }

import scala.concurrent.duration._

case class ScheduleSpec(
    id:                String,
    cron:              CronSpec,
    timeZone:          DateTimeZone      = ScheduleSpec.DefaultTimeZone,
    startingDeadline:  Duration          = ScheduleSpec.DefaultStartingDeadline,
    concurrencyPolicy: ConcurrencyPolicy = ScheduleSpec.DefaultConcurrencyPolicy,
    enabled:           Boolean           = ScheduleSpec.DefaultEnabled
) {
  def clock: Clock = ScheduleSpec.DefaultClock

  def nextExecution(after: DateTime): DateTime = {
    val localAfter = after.toDateTime(timeZone)
    val localNext = cron.nextExecution(localAfter)
    localNext.toDateTime(DateTimeZone.UTC)
  }

  def nextExecution(): DateTime = nextExecution(new DateTime(clock.instant().toEpochMilli, timeZone))
}

object ScheduleSpec {
  val DefaultTimeZone = DateTimeZone.UTC
  val DefaultStartingDeadline = 15.minutes
  val DefaultConcurrencyPolicy = ConcurrencyPolicy.Allow
  val DefaultEnabled = true
  val DefaultClock = Clock.systemUTC()

  implicit lazy val validScheduleSpec: Validator[ScheduleSpec] = validator[ScheduleSpec] { spec =>
    spec.startingDeadline >= 1.minute
  }
}

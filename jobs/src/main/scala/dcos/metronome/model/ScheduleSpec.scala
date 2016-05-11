package dcos.metronome.model

import org.joda.time.DateTimeZone

import scala.concurrent.duration._

case class ScheduleSpec(
  schedule: CronSpec,
  timeZone: DateTimeZone = ScheduleSpec.DefaultTimeZone,
  startingDeadline: Duration = ScheduleSpec.DefaultStartingDeadline,
  concurrencyPolicy: ConcurrencyPolicy = ScheduleSpec.DefaultConcurrencyPolicy,
  enabled: Boolean = ScheduleSpec.DefaultEnabled)

object ScheduleSpec {
  val DefaultTimeZone = DateTimeZone.UTC
  val DefaultStartingDeadline = 15.minutes
  val DefaultConcurrencyPolicy = ForbidConcurrentRuns
  val DefaultEnabled = false
}


package dcos.metronome.model

import org.joda.time.{ DateTime, DateTimeZone }

import scala.concurrent.duration._

case class ScheduleSpec(
    cron:              CronSpec,
    timeZone:          DateTimeZone      = ScheduleSpec.DefaultTimeZone,
    startingDeadline:  Duration          = ScheduleSpec.DefaultStartingDeadline,
    concurrencyPolicy: ConcurrencyPolicy = ScheduleSpec.DefaultConcurrencyPolicy,
    enabled:           Boolean           = ScheduleSpec.DefaultEnabled
) {

  def nextExecution(): DateTime = {
    cron.nextExecution()
  }
}

object ScheduleSpec {
  val DefaultTimeZone = DateTimeZone.UTC
  val DefaultStartingDeadline = 15.minutes
  val DefaultConcurrencyPolicy = ForbidConcurrentRuns
  val DefaultEnabled = false
}


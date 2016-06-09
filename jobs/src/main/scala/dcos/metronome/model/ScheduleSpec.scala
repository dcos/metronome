package dcos.metronome.model

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

  def nextExecution(after: DateTime): DateTime = {
    val localAfter = after.toDateTime(timeZone)
    val localNext = cron.nextExecution(localAfter)
    localNext.toDateTime(DateTimeZone.UTC)
  }
}

object ScheduleSpec {
  val DefaultTimeZone = DateTimeZone.UTC
  val DefaultStartingDeadline = 15.minutes
  val DefaultConcurrencyPolicy = ConcurrencyPolicy.Forbid
  val DefaultEnabled = false

  implicit lazy val validScheduleSpec: Validator[ScheduleSpec] = validator[ScheduleSpec] { spec =>
    spec.startingDeadline >= 1.minute
  }
}


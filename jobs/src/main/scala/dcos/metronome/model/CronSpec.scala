package dcos.metronome
package model

import java.time.{ Instant, ZoneId, ZonedDateTime }

import com.cronutils.model.definition.{ CronConstraint, CronDefinition, CronDefinitionBuilder }
import com.cronutils.model.time.ExecutionTime
import com.cronutils.model.Cron
import com.cronutils.model.field.expression.{ Between, On }
import com.cronutils.model.field.value.IntegerFieldValue
import com.cronutils.model.field.{ CronField, CronFieldName }
import com.cronutils.parser.CronParser
import org.threeten.bp.{ Instant => ThreeTenInstant, ZoneId => ThreeTenZoneId, ZonedDateTime => ThreeTenZonedDateTime }

import scala.util.control.NonFatal

class CronSpec(val cron: Cron) {

  private[this] lazy val executionTime: ExecutionTime = ExecutionTime.forCron(cron)

  def nextExecution(from: ZonedDateTime): ZonedDateTime = {
    val fromDateTime: ThreeTenZonedDateTime = jodaToThreetenTime(from)
    threetenToJodaTime(executionTime.nextExecution(fromDateTime).get())
  }

  def lastExecution(from: ZonedDateTime): ZonedDateTime = {
    val fromDateTime: ThreeTenZonedDateTime = jodaToThreetenTime(from)
    threetenToJodaTime(executionTime.lastExecution(fromDateTime).get())
  }

  private def threetenToJodaTime(from: ThreeTenZonedDateTime): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(from.toInstant.toEpochMilli), ZoneId.of(from.getZone.getId))
  }

  private def jodaToThreetenTime(from: ZonedDateTime): ThreeTenZonedDateTime = {
    ThreeTenZonedDateTime.ofInstant(ThreeTenInstant.ofEpochMilli(from.toInstant.toEpochMilli), ThreeTenZoneId.of(from.getZone.toString))
  }

  override def hashCode(): Int = cron.hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: CronSpec => other.cron.asString() == cron.asString()
    case _               => false
  }

  override def toString: String = cron.asString()
}

object CronSpec {
  val cronDefinition: CronDefinition =
    CronDefinitionBuilder.defineCron()
      .withMinutes().and()
      .withHours().and()
      .withDayOfMonth()
      .supportsHash().supportsL().supportsW().and()
      .withMonth().and()
      .withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).withIntMapping(7, 0) //we support non-standard non-zero-based numbers!
      .supportsHash().supportsL().supportsW().and()
      .withYear().optional().and()
      .matchDayOfWeekAndDayOfMonth() // the regular UNIX cron definition permits matching either DoW or DoM
      .withCronValidation(new CronDaysInMonthValidation)
      .instance()

  def isValid(cronString: String): Boolean = unapply(cronString).isDefined

  def apply(cronString: String): CronSpec = {
    new CronSpec(new CronParser(cronDefinition).parse(cronString))
  }

  def unapply(cronString: String): Option[CronSpec] = {
    try {
      Some(new CronSpec(new CronParser(cronDefinition).parse(cronString)))
    } catch {
      case NonFatal(_) => None
    }
  }
}

object CronSpecValidation {
  val validDayOfMonth = "Day of the month must exist in the provided month (e.g. February has only <= 29 days so running cron on Feb 30 is invalid)"
}

/**
  * Day of month validation is missing from cron-utils which will cause it to search endlessly for a day that doesn't exist.
  * This validator covers that use case disallowing schedules like 0 0 31 2 * (run on Feb 31.)
  */
class CronDaysInMonthValidation extends CronConstraint(CronSpecValidation.validDayOfMonth) {
  def daysExistInAMonths(days: Seq[Int], months: Seq[Int]): Boolean = {
    months.exists(m => days.exists(d => dayExistInAMonth(d, m)))
  }
  val maxDaysOfMonth = Array(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
  def dayExistInAMonth(day: Int, month: Int): Boolean = {
    day <= maxDaysOfMonth(month - 1)
  }

  def getValuesFromCron(field: CronField): Seq[Int] = field.getExpression match {
    case fieldValue: On => Seq(fieldValue.getTime.getValue)
    case fieldValue: Between =>
      (fieldValue.getFrom, fieldValue.getTo) match {
        case (f: IntegerFieldValue, t: IntegerFieldValue) => Array.range(f.getValue, t.getValue).toSeq
        case _ => Seq.empty
      }
    case _ => Seq.empty
  }

  override def validate(cron: Cron): Boolean = {
    val maybeDay = Option(cron.retrieve(CronFieldName.DAY_OF_MONTH))
    val maybeMonth = Option(cron.retrieve(CronFieldName.MONTH))

    (maybeDay, maybeMonth) match {
      case (Some(dayField), Some(monthField)) =>
        val days = getValuesFromCron(dayField)
        val months = getValuesFromCron(monthField)
        days.isEmpty || months.isEmpty || daysExistInAMonths(days, months)
      case _ => true // nothing to validate
    }
  }
}
package dcos.metronome.model

import com.cronutils.model.definition.{ CronDefinition, CronDefinitionBuilder }
import com.cronutils.model.time.ExecutionTime
import com.cronutils.model.Cron
import com.cronutils.parser.CronParser
import org.joda.time.{ DateTime, DateTimeZone }
import org.threeten.bp.{ Instant, ZoneId, ZonedDateTime }

import scala.util.control.NonFatal

class CronSpec(val cron: Cron) {

  private[this] lazy val executionTime: ExecutionTime = ExecutionTime.forCron(cron)

  def nextExecution(from: DateTime): DateTime = {
    val fromDateTime: ZonedDateTime = jodaToThreetenTime(from)
    threetenToJodaTime(executionTime.nextExecution(fromDateTime).get())
  }

  def lastExecution(from: DateTime): DateTime = {
    val fromDateTime: ZonedDateTime = jodaToThreetenTime(from)
    threetenToJodaTime(executionTime.lastExecution(fromDateTime).get())
  }

  private def threetenToJodaTime(from: ZonedDateTime): DateTime = {
    new DateTime(from.toInstant().toEpochMilli(), DateTimeZone.forID(from.getZone().getId()))
  }

  private def jodaToThreetenTime(from: DateTime): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(from.getMillis()), ZoneId.of(from.getZone().getID))
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
      .withDayOfWeek()
      .withIntMapping(7, 0) //we support non-standard non-zero-based numbers!
      .supportsHash().supportsL().supportsW().and()
      .withYear().optional().and()
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

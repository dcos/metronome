package dcos.metronome.model

import com.cronutils.model.definition.{ CronDefinition, CronDefinitionBuilder }
import com.cronutils.model.time.ExecutionTime
import com.cronutils.model.Cron
import com.cronutils.parser.CronParser
import org.joda.time.DateTime

import scala.util.control.NonFatal

class CronSpec(val cron: Cron) {

  private[this] lazy val executionTime: ExecutionTime = ExecutionTime.forCron(cron)

  def nextExecution(from: DateTime): DateTime = executionTime.nextExecution(from)

  def lastExecution(from: DateTime): DateTime = executionTime.lastExecution(from)

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
      .withYear().and()
      .lastFieldOptional()
      .instance()

  def isValid(cronString: String): Boolean = unapply(cronString).isDefined

  def unapply(cronString: String): Option[CronSpec] = {
    try {
      Some(new CronSpec(new CronParser(cronDefinition).parse(cronString)))
    } catch {
      case NonFatal(_) => None
    }
  }
}

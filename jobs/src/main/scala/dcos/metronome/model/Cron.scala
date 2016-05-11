package dcos.metronome.model

import com.cronutils.model.definition.{CronDefinition, CronDefinitionBuilder}
import com.cronutils.model.time.ExecutionTime
import com.cronutils.model.{Cron => UCron}
import com.cronutils.parser.CronParser
import org.joda.time.DateTime

import scala.util.control.NonFatal

class Cron(val cron: UCron) {

  private[this] lazy val executionTime: ExecutionTime = ExecutionTime.forCron(cron)

  def nextExecution(now: DateTime = DateTime.now()): DateTime = executionTime.nextExecution(now)

  def lastExecution(now: DateTime = DateTime.now()): DateTime = executionTime.lastExecution(now)

  override def hashCode(): Int = cron.hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: Cron => other.cron.asString() == cron.asString()
    case _           => false
  }

  override def toString: String = cron.asString()
}

object Cron {
  val cronDefinition: CronDefinition =
    CronDefinitionBuilder.defineCron()
      .withSeconds().and()
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

  def isValid(cronString: String): Boolean = apply(cronString).isDefined

  def apply(cronString: String): Option[Cron] = {
    try {
      Some(new Cron(new CronParser(cronDefinition).parse(cronString)))
    } catch {
      case NonFatal(_) => None
    }
  }
}

package dcos.metronome.model

import it.sauronsoftware.cron4j.Predictor
import org.joda.time.DateTime

import scala.util.control.NonFatal

class CronSpec(val cron: String) {

  new Predictor(cron)

  def nextExecution(from: DateTime): DateTime = new DateTime(new Predictor(cron, from.getMillis).nextMatchingDate());

  override def hashCode(): Int = cron.hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: CronSpec => other.cron == cron
    case _               => false
  }

  override def toString: String = cron
}

object CronSpec {

  def isValid(cronString: String): Boolean = unapply(cronString).isDefined

  def apply(cronString: String): CronSpec = {
    new CronSpec(cronString)
  }

  def unapply(cronString: String): Option[CronSpec] = {
    try {
      Some(new CronSpec(cronString))
    } catch {
      case NonFatal(_) => None
    }
  }
}

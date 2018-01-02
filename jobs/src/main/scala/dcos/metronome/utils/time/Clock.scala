package dcos.metronome
package utils.time

import org.joda.time.{ DateTimeZone, DateTime }

import scala.concurrent.duration.FiniteDuration

trait Clock {
  def now(): DateTime
}

class SystemClock(dateTimeZone: DateTimeZone = DateTimeZone.UTC) extends Clock {
  override def now(): DateTime = DateTime.now(dateTimeZone)
}

class FixedClock(var _now: DateTime) extends Clock {
  private[this] var subscribers: List[() => Unit] = Nil
  def onChange(fn: () => Unit): Unit = synchronized {
    subscribers = fn :: subscribers
  }

  override def now(): DateTime = _now

  def +=(duration: FiniteDuration): Unit = _now = _now.plusMillis(duration.toMillis.toInt)
}

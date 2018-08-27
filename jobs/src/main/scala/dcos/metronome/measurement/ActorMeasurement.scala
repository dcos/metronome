package dcos.metronome
package measurement

import akka.actor.{ Actor, ActorLogging }

import scala.util.control.NonFatal

trait ActorMeasurement { actor: Actor with ActorLogging =>

  def measurement: ServiceMeasurement

  /**
    * The metrics logic is wrapped inside this method.
    * It wraps the original receive method.
    */
  private[this] def wrapped(receive: Receive): Receive = {
    try {
      timePartialFunction(receive)
    } catch {
      case NonFatal(ex) =>
        measurement.metrics.counter(s"${actor.getClass}.receiveExceptionMeter").increment()
        throw ex
    }
  }

  /**
    * Migration of method from metrics scala
    * Converts partial function `pf` into a side-effecting partial function that times
    * every invocation of `pf` for which it is defined. The result is passed unchanged.
    */
  private def timePartialFunction[A, B](pf: PartialFunction[A, B]): PartialFunction[A, B] = new PartialFunction[A, B] {
    def apply(a: A): B = {
      measurement.metrics.timer(s"${actor.getClass}.receiveExceptionMeter").blocking(pf.apply(a))
    }

    def isDefinedAt(a: A): Boolean = pf.isDefinedAt(a)
  }

  protected def measure(receive: Receive): Receive = {
    log.debug(s"Create actor metrics for actor: ${actor.getClass.getName}")
    if (measurement.config.withMetrics) wrapped(receive) else receive
  }
}
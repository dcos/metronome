package dcos.metronome
package measurement

import akka.actor.{ Actor, ActorLogging }
import mesosphere.marathon.metrics.{ Metrics, ServiceMetric }

import scala.util.control.NonFatal

trait ActorMeasurement { actor: Actor with ActorLogging =>

  def measurement: ServiceMeasurement

  /**
    * This timer measures the time for message receipt.
    */
  private[this] lazy val receiveTimer = Metrics.timer(ServiceMetric, actor.getClass, "receiveTimer")

  /**
    * This meter counts the thrown exceptions in the receiver.
    */
  private[this] lazy val exceptionMeter = Metrics.minMaxCounter(ServiceMetric, actor.getClass, "receiveExceptionMeter")

  /**
    * The metrics logic is wrapped inside this method.
    * It wraps the original receive method.
    */
  private[this] def wrapped(receive: Receive): Receive = {
    try {
      timePartialFunction(receive)
    } catch {
      case NonFatal(ex) =>
        exceptionMeter.increment()
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
      receiveTimer.blocking(pf.apply(a))
    }

    def isDefinedAt(a: A): Boolean = pf.isDefinedAt(a)
  }

  protected def measure(receive: Receive): Receive = {
    log.debug(s"Create actor metrics for actor: ${actor.getClass.getName}")
    if (measurement.config.withMetrics) wrapped(receive) else receive
  }
}
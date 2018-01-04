package dcos.metronome
package behavior

import akka.actor.{ Actor, ActorLogging }
import nl.grons.metrics.scala.Meter

import scala.util.control.NonFatal

trait ActorBehavior { actor: Actor with ActorLogging =>

  def behavior: Behavior

  /**
    * The metric builder is used to create the metrics used in this actor.
    */
  private[this] lazy val metricBuilder = behavior.metrics.builder(actor.getClass)

  /**
    * This timer measures the time for message receipt.
    */
  private[this] lazy val receiveTimer = metricBuilder.timer("receiveTimer")

  /**
    * This meter counts the thrown exceptions in the receiver.
    */
  private[this] lazy val exceptionMeter: Meter = metricBuilder.meter("receiveExceptionMeter")

  /**
    * The metrics logic is wrapped inside this method.
    * It wraps the original receive method.
    */
  private[this] def wrapped(receive: Receive): Receive = {
    try {
      receiveTimer.timePF(receive)
    } catch {
      case NonFatal(ex) =>
        exceptionMeter.mark()
        throw ex
    }
  }

  protected def around(receive: Receive): Receive = {
    log.debug(s"Create actor metrics for actor: ${actor.getClass.getName}")
    if (behavior.config.withMetrics) wrapped(receive) else receive
  }
}

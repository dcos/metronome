package dcos.metronome
package measurement

import mesosphere.marathon.metrics.Metrics

import scala.reflect.ClassTag

/**
  * This trait defines a way to measure time spent in a class methods.
  * It is configurable via the MeasurementConfig.
  */
trait ServiceMeasurement {

  /**
    * Use this method for wiring services (construction time only!)
    * This will eventually (if configured) create a proxy, that implements T and adds the configured measurement code.
    *
    * @param t the object to add the behavior
    * @param classTag the classTag of t
    * @tparam T the type of T
    * @return a t with the configured behavior
    */
  def apply[T <: AnyRef](t: T)(implicit classTag: ClassTag[T]): T

  /**
    * Note: This is needed to enable gathering metrics.
    */
  def config: MeasurementConfig

  def metrics: Metrics
}

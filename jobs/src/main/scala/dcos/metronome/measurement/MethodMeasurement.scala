package dcos.metronome
package measurement

import scala.reflect.ClassTag

/**
  * This trait defines configurable system behavior.
  * The behavior is configurable via the MeasurementConfig.
  *
  * Supported behaviors:
  *  - metric support for services and actors
  */
trait MethodMeasurement {

  /**
    * Use this method for wiring services (construction time only!)
    * This will eventually (if configured) create a proxy, that implements T and adds the configured behavior.
    *
    * @param t the object to add the behavior
    * @param classTag the classTag of t
    * @tparam T the type of T
    * @return a t with the configured behavior
    */
  def apply[T <: AnyRef](t: T)(implicit classTag: ClassTag[T]): T

  /**
    * The behavior configuration.
    * Note: This is needed for enabling actor behavior, which has to be handled inside the actor.
    *
    * @return the behavior configuration.
    */
  def config: MeasurementConfig
}

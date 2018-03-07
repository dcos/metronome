package dcos.metronome.measurement
import scala.reflect.ClassTag

object MethodMeasurementFixture {

  def empty: MethodMeasurement = new MethodMeasurement {
    /**
      * Use this method for wiring services (construction time only!)
      * This will eventually (if configured) create a proxy, that implements T and adds the configured behavior.
      *
      * @param t        the object to add the behavior
      * @param classTag the classTag of t
      * @tparam T the type of T
      * @return a t with the configured behavior
      */
    override def apply[T <: AnyRef](t: T)(implicit classTag: ClassTag[T]): T = t

    /**
      * The behavior configuration.
      * Note: This is needed for enabling actor behavior, which has to be handled inside the actor.
      *
      * @return the behavior configuration.
      */
    override def config: MeasurementConfig = new MeasurementConfig {
      override def withMetrics: Boolean = false
    }
  }
}

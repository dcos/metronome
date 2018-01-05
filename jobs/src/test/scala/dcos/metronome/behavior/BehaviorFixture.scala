package dcos.metronome
package behavior

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import dcos.metronome.behavior.impl.MetricsImpl

import scala.reflect.ClassTag

object BehaviorFixture {

  def empty: Behavior = new Behavior {

    override val metrics = new MetricsImpl(new MetricRegistry, new HealthCheckRegistry)

    override def config: BehaviorConfig = new BehaviorConfig {
      override def withMetrics: Boolean = false
    }

    override def apply[T <: AnyRef](t: T)(implicit classTag: ClassTag[T]): T = t
  }
}

package dcos.metronome
package behavior.impl

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import dcos.metronome.behavior.Metrics
import nl.grons.metrics.scala._

import scala.reflect.ClassTag

class MetricsImpl(val metricRegistry: MetricRegistry, val registry: HealthCheckRegistry)
    extends InstrumentedBuilder with CheckedBuilder with Metrics {

  override def builder[T](implicit classTag: ClassTag[T]): MetricBuilder = {
    new MetricBuilder(MetricName(classTag.runtimeClass), metricRegistry)
  }

  override def builder(forClass: Class[_]): MetricBuilder = {
    new MetricBuilder(MetricName(forClass), metricRegistry)
  }
}

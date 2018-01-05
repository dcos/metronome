package dcos.metronome
package behavior

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import dcos.metronome.behavior.impl.{ BehaviorImpl, MetricsImpl }

class BehaviorModule(config: BehaviorConfig, metricRegistry: MetricRegistry, healthCheckRegistry: HealthCheckRegistry) {

  lazy val metrics: Metrics = new MetricsImpl(metricRegistry, healthCheckRegistry)

  lazy val behavior = new BehaviorImpl(config, metrics)
}

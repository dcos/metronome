package dcos.metronome

import java.lang.management.ManagementFactory

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.codahale.metrics.jvm.{ BufferPoolMetricSet, GarbageCollectorMetricSet, MemoryUsageGaugeSet, ThreadStatesGaugeSet }
import mesosphere.marathon.metrics.Metrics

class MetricsModule {

  lazy val healthCheckRegistry: HealthCheckRegistry = new HealthCheckRegistry()

  lazy val metricsRegistry: MetricRegistry = {
    val registry = new MetricRegistry
    registry.register("jvm.gc", new GarbageCollectorMetricSet())
    registry.register("jvm.buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer))
    registry.register("jvm.memory", new MemoryUsageGaugeSet())
    registry.register("jvm.threads", new ThreadStatesGaugeSet())
    registry
  }

  lazy val metrics = new Metrics(metricsRegistry)
}

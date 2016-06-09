package dcos.metronome.behavior

import com.codahale.metrics.MetricRegistry
import nl.grons.metrics.scala.{ MetricBuilder, Timer }

import scala.reflect.ClassTag

trait Metrics {

  def metricRegistry: MetricRegistry

  def builder[T](implicit classTag: ClassTag[T]): MetricBuilder

  def builder(forClass: Class[_]): MetricBuilder
}

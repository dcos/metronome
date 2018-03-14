package dcos.metronome
package measurement.impl

import java.lang.reflect.Method

import com.softwaremill.macwire.aop.{ Interceptor, ProxyingInterceptor }
import dcos.metronome.measurement.{ MethodMeasurement, MeasurementConfig }
import mesosphere.marathon.metrics.{ Metrics, MinMaxCounter, ServiceMetric, Timer }
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class KamonServiceMeasurement(val config: MeasurementConfig) extends MethodMeasurement {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def apply[T <: AnyRef](t: T)(implicit classTag: ClassTag[T]): T = {
    if (config.withMetrics) timedInvocationWithExceptionCount(classTag).apply(t) else t
  }

  def timedInvocationWithExceptionCount[T](classTag: ClassTag[T]): Interceptor = {
    var methodTimer = Map.empty[String, Timer]
    def timer(method: Method): Timer = {
      methodTimer.getOrElse(method.getName, {
        log.debug(s"Create new timer for method: ${method.getName} in class ${classTag.runtimeClass.getName}")
        val timer = Metrics.timer(ServiceMetric, classTag.runtimeClass, s"${method.getName}")
        methodTimer += method.getName -> timer
        timer
      })
    }
    var methodExceptionCount = Map.empty[String, MinMaxCounter]
    def methodException(method: Method, ex: Throwable): MinMaxCounter = {
      val name = s"exception.${method.getName}.${ex.getClass.getName}"
      methodExceptionCount.getOrElse(name, {
        log.debug(s"Create new count for method: ${method.getName} exception: ${ex.getClass.getName} in class ${classTag.runtimeClass.getName}")
        val counter = Metrics.minMaxCounter(ServiceMetric, classTag.runtimeClass, name)
        methodExceptionCount += name -> counter
        counter
      })
    }
    ProxyingInterceptor.apply { ctx =>
      val metricTimer = timer(ctx.method)
      println(s"Is assignable? ${ctx.method.getReturnType.isAssignableFrom(classOf[Future[Any]])}")
      if (ctx.method.getReturnType.isAssignableFrom(classOf[Future[Any]])) {
        import mesosphere.util.CallerThreadExecutionContext.callerThreadExecutionContext
        metricTimer.apply(ctx.proceed().asInstanceOf[Future[Any]]).recover {
          case NonFatal(ex: Throwable) =>
            methodException(ctx.method, ex).increment()
            throw ex
        }
      } else {
        metricTimer.blocking(ctx.proceed())
      }
    }
  }
}

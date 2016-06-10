package dcos.metronome.behavior.impl

import java.lang.reflect.Method

import com.softwaremill.macwire.aop.{ Interceptor, ProxyingInterceptor }
import dcos.metronome.behavior.{ BehaviorConfig, Behavior, Metrics }
import nl.grons.metrics.scala.{ Meter, Timer }
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class BehaviorImpl(val config: BehaviorConfig, val metrics: Metrics) extends Behavior {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def apply[T <: AnyRef](t: T)(implicit classTag: ClassTag[T]): T = {
    if (config.withMetrics) timedInvocationWithExceptionCount(classTag).apply(t) else t
  }

  def timedInvocationWithExceptionCount[T](classTag: ClassTag[T]): Interceptor = {
    val builder = metrics.builder(classTag)
    var methodTimer = Map.empty[String, Timer]
    def timer(method: Method): Timer = {
      methodTimer.getOrElse(method.getName, {
        log.debug(s"Create new timer for method: ${method.getName} in class ${classTag.runtimeClass.getName}")
        val timer = builder.timer(method.getName)
        methodTimer += method.getName -> timer
        timer
      })
    }
    var methodExceptionCount = Map.empty[String, Meter]
    def methodException(method: Method, ex: Throwable): Meter = {
      val name = method.getName + ex.getClass.getName
      methodExceptionCount.getOrElse(name, {
        log.debug(s"Create new count for method: ${method.getName} exception: ${ex.getClass.getName} in class ${classTag.runtimeClass.getName}")
        val meter = builder.meter(method.getName)
        methodExceptionCount += name -> meter
        meter
      })
    }
    ProxyingInterceptor.apply { ctx =>
      val metricTimer = timer(ctx.method)
      if (ctx.method.getReturnType.isAssignableFrom(classOf[Future[Any]])) {
        import mesosphere.util.CallerThreadExecutionContext.callerThreadExecutionContext
        metricTimer.timeFuture(ctx.proceed().asInstanceOf[Future[Any]]).recover {
          case NonFatal(ex: Throwable) =>
            methodException(ctx.method, ex).mark()
            throw ex
        }
      } else {
        metricTimer.time(ctx.proceed())
      }
    }
  }
}

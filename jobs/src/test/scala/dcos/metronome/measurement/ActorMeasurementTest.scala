package dcos.metronome
package behavior

import akka.Done
import akka.actor.{ Actor, ActorLogging, ActorRefFactory, ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit }
import dcos.metronome.measurement.impl.KamonServiceMeasurement
import dcos.metronome.measurement.{ ActorMeasurement, MeasurementConfig, MethodMeasurement }
import kamon.Kamon
import kamon.metric.{ DefaultEntitySnapshot, Entity, EntitySnapshot }
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ CollectionContext, CompactHdrSnapshot, Histogram }
import mesosphere.marathon.metrics.Metrics
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ FunSuiteLike, GivenWhenThen, Matchers }

import scala.concurrent.Promise

class ActorMeasurementTest extends TestKit(ActorSystem("test")) with FunSuiteLike with GivenWhenThen with ScalaFutures with Matchers {

  def metricsOfActor(actorName: String) = Metrics.snapshot().metrics.filter {
    case (entity, _) => entity.name.contains("DummyActorWithMeasurement")
  }

  def numberOfMeasurements(timers: Map[Entity, EntitySnapshot]) = timers.head._2.metrics.head._2.asInstanceOf[CompactHdrSnapshot].numberOfMeasurements

  test("Actors with active metrics") {

    Given("A dummy actor with ActorMetrics")
    val f = new Fixture
    val actor = f.dummyActorWithMetrics(f.measurementWithMetrics)
    val promise1 = Promise[Boolean]
    val promise2 = Promise[Boolean]
    val promise3 = Promise[Boolean]

    When("Sending 3 promises to the actor")
    actor ! DummyPromise(promise1)
    actor ! DummyPromise(promise2)
    actor ! DummyPromise(promise3)
    promise1.future.futureValue
    promise2.future.futureValue
    promise3.future.futureValue

    Then("Timer count should be 3")
    Thread.sleep(1000) // we have to wait for tick-period, that is defined in application.conf
    val timers = metricsOfActor("DummyActorWithMeasurement")
    timers should have size 1
    numberOfMeasurements(timers) shouldBe 3
  }

  test("Actors with disabled metrics") {

    Given("A dummy actor with ActorMetrics")
    val initialMeasurements = numberOfMeasurements(metricsOfActor("DummyActorWithMeasurement"))
    val f = new Fixture
    val actor = f.dummyActorWithMetrics(f.measurementWithoutMetrics)
    val promise1 = Promise[Boolean]
    val promise2 = Promise[Boolean]
    val promise3 = Promise[Boolean]

    When("Sending 3 promises to the actor")
    actor ! DummyPromise(promise1)
    actor ! DummyPromise(promise2)
    actor ! DummyPromise(promise3)

    promise1.future.futureValue
    promise2.future.futureValue
    promise3.future.futureValue

    Then("No new measurements should be recorded")
    Thread.sleep(1000) // we have to wait for tick-period, that is defined in application.conf
    val finalMeasurements = numberOfMeasurements(metricsOfActor("DummyActorWithMeasurement"))
    finalMeasurements should be(initialMeasurements)
  }

  class Fixture {
    val measurementWithMetrics = new KamonServiceMeasurement(new MeasurementConfig {
      override def withMetrics: Boolean = true
    })

    val measurementWithoutMetrics = new KamonServiceMeasurement(new MeasurementConfig {
      override def withMetrics: Boolean = false
    })

    Kamon.start()
    Metrics.start(ActorSystem("metrics"))

    def dummyActorWithMetrics(behavior: MethodMeasurement) = TestActorRef[DummyActorWithMeasurement](Props(new DummyActorWithMeasurement(behavior)))
  }
}

class DummyActorWithMeasurement(val measurement: MethodMeasurement) extends Actor with ActorLogging with ActorMeasurement {
  override def receive: Receive = measure {
    case DummyPromise(promise) => promise.success(true)
  }
}
case class DummyPromise(promise: Promise[Boolean])
case object DummyException extends Exception
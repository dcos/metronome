package dcos.metronome.behavior

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit }
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ FunSuiteLike, GivenWhenThen, Matchers }

import scala.concurrent.Promise

class ActorBehaviorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with GivenWhenThen with ScalaFutures with Matchers {

  test("Actors with active metrics") {

    Given("A dummy actor with ActorMetrics")
    val f = new Fixture
    val actor = f.dummyActorWithMetrics(f.behaviorModuleWithMetrics.behavior)
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
    val timers = f.behaviorModuleWithMetrics.metrics.metricRegistry.getTimers()
    timers should have size 1
    val timer = f.behaviorModuleWithMetrics.metrics.metricRegistry.getTimers.get(timers.firstKey())
    timer.getCount shouldBe 3
  }

  test("Actors with disabled metrics") {

    Given("A dummy actor with ActorMetrics")
    val f = new Fixture
    val actor = f.dummyActorWithMetrics(f.behaviorModuleWithoutMetrics.behavior)
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

    Then("Timer count should be 0")
    f.behaviorModuleWithoutMetrics.metrics.metricRegistry.getTimers() should have size 0
  }

  class Fixture {
    val behaviorModuleWithMetrics = new BehaviorModule(new BehaviorConfig {
      override def withMetrics: Boolean = true
    }, new MetricRegistry(), new HealthCheckRegistry)

    val behaviorModuleWithoutMetrics = new BehaviorModule(new BehaviorConfig {
      override def withMetrics: Boolean = false
    }, new MetricRegistry(), new HealthCheckRegistry)

    def dummyActorWithMetrics(behavior: Behavior) = TestActorRef[DummyActorWithBehavior](Props(new DummyActorWithBehavior(behavior)))
  }
}

class DummyActorWithBehavior(val behavior: Behavior) extends Actor with ActorLogging with ActorBehavior {
  override def receive: Receive = around {
    case DummyPromise(promise) => promise.success(true)
  }
}
case class DummyPromise(promise: Promise[Boolean])
case object DummyException extends Exception
package dcos.metronome
package jobspec.impl

import akka.actor.ActorSystem
import akka.testkit._
import dcos.metronome.model.{ JobId, JobSpec }
import dcos.metronome.repository.Repository
import dcos.metronome.utils.test.Mockito
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class JobSpecPersistenceActorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with Matchers with Eventually with ImplicitSender with Mockito {

  import JobSpecPersistenceActor._
  import system.dispatcher

  test("Only one change operation is applied at a time") {
    Given("A persistence actor")
    val latch = new TestLatch()
    val f = new Fixture
    val actor = f.persistenceActor
    f.repository.create(any, any) returns Future {
      Await.ready(latch, 3.seconds)
      f.jobSpec
    }
    f.repository.update(any, any) returns Future.successful(f.jobSpec)
    f.repository.delete(any) returns Future.successful(true)

    When("A create message is sent")
    actor ! Create(f.jobSpec, self)
    actor ! Update(identity, self)
    actor ! Delete(f.jobSpec, self)

    Then("only the create call is executed")
    eventually(verify(f.repository).create(any, any))
    noMoreInteractions(f.repository)
    latch.open()
    expectMsg(Created(self, f.jobSpec, self))
    expectMsg(Updated(self, f.jobSpec, self))
    expectMsg(Deleted(self, f.jobSpec, self))
    system.stop(actor)
  }

  test("Create operation is successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    f.repository.create(any, any) returns Future.successful(f.jobSpec)

    When("A create message is sent")
    actor ! Create(f.jobSpec, self)

    Then("A creates message is replied")
    expectMsg(Created(self, f.jobSpec, self))
  }

  test("Create operation is not successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val exception = new RuntimeException("boom")
    f.repository.create(f.id, f.jobSpec) returns Future.failed(exception)

    When("A create message is sent")
    actor ! Create(f.jobSpec, self)

    Then("A failed message is sent")
    expectMsg(PersistFailed(self, f.jobSpec.id, exception, self))
  }

  test("Update operation is successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val changed = f.jobSpec.copy(description = Some("changed"))
    f.repository.update(any, any) returns Future.successful(changed)

    When("A update message is sent")
    actor ! Update(_ => changed, self)

    Then("An ack message is sent")
    expectMsg(Updated(self, changed, self))
  }

  test("Update operation is not successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val exception = new RuntimeException("boom")
    f.repository.update(any, any) returns Future.failed(exception)

    When("An update message is sent")
    actor ! Update(_ => f.jobSpec, self)

    Then("A failed message is sent")
    expectMsg(PersistFailed(self, f.jobSpec.id, exception, self))
  }

  test("Delete operation is successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    f.repository.delete(f.id) returns Future.successful(true)

    When("A delete message is sent")
    actor ! Delete(f.jobSpec, self)

    Then("An ack message is sent")
    expectMsg(Deleted(self, f.jobSpec, self))
  }

  test("Delete operation is not successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val exception = new RuntimeException("boom")
    f.repository.delete(f.id) returns Future.failed(exception)

    When("An update message is sent")
    actor ! Delete(f.jobSpec, self)

    Then("A failed message is sent")
    expectMsg(PersistFailed(self, f.jobSpec.id, exception, self))
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val repository = mock[Repository[JobId, JobSpec]]
    val id = JobId("/test")
    val jobSpec = JobSpec(id)
    val behavior = BehaviorFixture.empty
    def persistenceActor = system.actorOf(JobSpecPersistenceActor.props(id, repository, behavior))
  }
}

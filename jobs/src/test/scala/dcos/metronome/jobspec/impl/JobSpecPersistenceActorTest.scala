package dcos.metronome.jobspec.impl

import akka.actor.ActorSystem
import akka.testkit._
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.Repository
import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.state.PathId
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._

class JobSpecPersistenceActorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with Matchers with Eventually with ImplicitSender with Mockito {

  import JobSpecPersistenceActor._
  import system.dispatcher

  test("Only one change operation is applied at a time") {
    Given("A persistence actor")
    val latch = new TestLatch()
    val f = new Fixture
    val actor = f.persistenceActor
    val createPromise = Promise[JobSpec]
    val updatePromise = Promise[JobSpec]
    val deletePromise = Promise[JobSpec]
    f.repository.create(any, any) returns Future {
      Await.ready(latch, 3.seconds)
      f.jobSpec
    }
    f.repository.update(any, any) returns Future.successful(f.jobSpec)
    f.repository.delete(any) returns Future.successful(true)

    When("A create message is send")
    actor ! Create(f.jobSpec, createPromise)
    actor ! Update(identity, updatePromise)
    actor ! Delete(f.jobSpec, deletePromise)

    Then("only the create call is executed")
    verify(f.repository).create(any, any)
    noMoreInteractions(f.repository)
    latch.open()
    expectMsg(Created(self, f.jobSpec, createPromise))
    expectMsg(Updated(self, f.jobSpec, updatePromise))
    expectMsg(Deleted(self, f.jobSpec, deletePromise))
    system.stop(actor)
  }

  test("Create operation is successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val promise = Promise[JobSpec]
    f.repository.create(any, any) returns Future.successful(f.jobSpec)

    When("A create message is send")
    actor ! Create(f.jobSpec, promise)

    Then("A creates message is replied")
    expectMsg(Created(self, f.jobSpec, promise))
  }

  test("Create operation is not successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val promise = Promise[JobSpec]
    val exception = new RuntimeException("boom")
    f.repository.create(f.id, f.jobSpec) returns Future.failed(exception)

    When("A create message is send")
    actor ! Create(f.jobSpec, promise)

    Then("A failed message is send")
    expectMsg(PersistFailed(self, f.jobSpec.id, exception, promise))
  }

  test("Update operation is successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val promise = Promise[JobSpec]
    val changed = f.jobSpec.copy(description = "changed")
    f.repository.update(any, any) returns Future.successful(changed)

    When("A update message is send")
    actor ! Update(_ => changed, promise)

    Then("An ack message is send")
    expectMsg(Updated(self, changed, promise))
  }

  test("Update operation is not successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val promise = Promise[JobSpec]
    val exception = new RuntimeException("boom")
    f.repository.update(any, any) returns Future.failed(exception)

    When("An update message is send")
    actor ! Update(_ => f.jobSpec, promise)

    Then("A failed message is send")
    expectMsg(PersistFailed(self, f.jobSpec.id, exception, promise))
  }

  test("Delete operation is successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val promise = Promise[JobSpec]
    f.repository.delete(f.id) returns Future.successful(true)

    When("A delete message is send")
    actor ! Delete(f.jobSpec, promise)

    Then("An ack message is send")
    expectMsg(Deleted(self, f.jobSpec, promise))
  }

  test("Delete operation is not successful") {
    Given("A persistence actor")
    val f = new Fixture
    val actor = f.persistenceActor
    val promise = Promise[JobSpec]
    val exception = new RuntimeException("boom")
    f.repository.delete(f.id) returns Future.failed(exception)

    When("An update message is send")
    actor ! Delete(f.jobSpec, promise)

    Then("A failed message is send")
    expectMsg(PersistFailed(self, f.jobSpec.id, exception, promise))
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val repository = mock[Repository[PathId, JobSpec]]
    val id = PathId("/test")
    val jobSpec = JobSpec(id, "test")
    def persistenceActor = system.actorOf(JobSpecPersistenceActor.props(id, repository))
  }
}

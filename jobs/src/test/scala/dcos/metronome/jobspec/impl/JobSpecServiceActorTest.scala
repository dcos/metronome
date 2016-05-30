package dcos.metronome.jobspec.impl

import java.io.IOException
import java.util.concurrent.LinkedBlockingDeque

import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit._
import dcos.metronome.{ JobSpecDoesNotExist, JobSpecChangeInFlight, JobSpecAlreadyExists }
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.impl.InMemoryRepository
import mesosphere.marathon.state.PathId
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ Matchers, GivenWhenThen, BeforeAndAfterAll, FunSuiteLike }

import scala.concurrent.Promise

class JobSpecServiceActorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with Matchers with Eventually with ImplicitSender {

  import JobSpecServiceActor._
  import JobSpecPersistenceActor._

  test("An existing jobSpec can be retrieved") {
    Given("A service with one jobs")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[Option[JobSpec]]
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A jobSpec is created")
    service ! GetJobSpec(f.jobSpec.id, promise)

    Then("The jobSpec is available")
    promise.future.futureValue should be(Some(f.jobSpec))
  }

  test("A non existing jobSpec can be retrieved") {
    Given("A service with no jobs")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[Option[JobSpec]]

    When("A jobSpec is created")
    service ! GetJobSpec(f.jobSpec.id, promise)

    Then("The jobSpec is available")
    promise.future.futureValue should be(None)
  }

  test("All available jobSpecs can be retrieved") {
    Given("A service with 3 jobs")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[Iterable[JobSpec]]
    service.underlyingActor.addJobSpec(f.jobSpec.copy(id = PathId("/test/one")))
    service.underlyingActor.addJobSpec(f.jobSpec.copy(id = PathId("/test/two")))
    service.underlyingActor.addJobSpec(f.jobSpec.copy(id = PathId("/test/three")))

    When("A jobSpec is created")
    service ! ListJobSpecs(_ => true, promise)

    Then("The jobSpec is available")
    promise.future.futureValue should have size 3
  }

  test("A jobSpec can be created") {
    Given("A service with no jobs")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]

    When("A jobSpec is created")
    service ! CreateJobSpec(f.jobSpec, promise)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! Created(self, f.jobSpec, promise)

    Then("The jobSpec is available")
    promise.future.futureValue should be(f.jobSpec)
    service.underlyingActor.allJobs should have size 1
    service.underlyingActor.allJobs(f.jobSpec.id) should be (f.jobSpec)
    service.underlyingActor.inFlightChanges should have size 0
  }

  test("A jobSpec can not be created, if it exists") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is created")
    service ! CreateJobSpec(f.jobSpec, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(JobSpecAlreadyExists(f.jobSpec.id))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
  }

  test("A jobSpec can not be created, if the repository fails") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    val exception = new RuntimeException("failed")

    When("An existing jobSpec is created")
    service ! CreateJobSpec(f.jobSpec, promise)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! PersistFailed(self, f.jobSpec.id, exception, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(exception)
    eventually(service.underlyingActor.inFlightChanges should have size 0)
    service.underlyingActor.allJobs should have size 0
  }

  test("A jobSpec can be updated") {
    Given("A service with one jobs")
    val f = new Fixture
    val changed = f.jobSpec.copy(description = "chnanged")
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A jobSpec is updated")
    service ! UpdateJobSpec(f.jobSpec.id, _ => changed, promise)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! Updated(self, changed, promise)

    Then("The jobSpec is updated")
    promise.future.futureValue should be(changed)
    service.underlyingActor.allJobs should have size 1
    service.underlyingActor.allJobs(f.jobSpec.id) should be (changed)
    service.underlyingActor.inFlightChanges should have size 0
  }

  test("A jobSpec can not be updated, if it exists in the actor") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]

    When("A non existing jobSpec is updated")
    service ! UpdateJobSpec(f.jobSpec.id, identity, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(JobSpecDoesNotExist(f.jobSpec.id))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
  }

  test("A jobSpec can not be updated, if there is a change in flight") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is updated twice")
    service ! UpdateJobSpec(f.jobSpec.id, identity, Promise[JobSpec])
    service ! UpdateJobSpec(f.jobSpec.id, identity, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(JobSpecChangeInFlight(f.jobSpec.id))
    eventually(service.underlyingActor.inFlightChanges should have size 1)
  }

  test("A jobSpec can not be updated, if the repository fails") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    val exception = new RuntimeException("failed")
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is updated and the repo fails")
    service ! UpdateJobSpec(f.jobSpec.id, identity, promise)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! PersistFailed(self, f.jobSpec.id, exception, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(exception)
    eventually(service.underlyingActor.inFlightChanges should have size 0)
    service.underlyingActor.allJobs should have size 1
  }

  test("A jobSpec can be deleted") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A jobSpec is created")
    service ! DeleteJobSpec(f.jobSpec.id, promise)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! Deleted(self, f.jobSpec, promise)

    Then("The jobSpec is available")
    promise.future.futureValue should be(f.jobSpec)
    service.underlyingActor.allJobs should have size 0
    service.underlyingActor.inFlightChanges should have size 0
  }

  test("A jobSpec can not be deleted, if it does not exist") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]

    When("A change is already in flight")
    service ! DeleteJobSpec(f.jobSpec.id, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(JobSpecDoesNotExist(f.jobSpec.id))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
  }

  test("A jobSpec can not be deleted, if there is a change in flight") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A change is already in flight")
    service ! DeleteJobSpec(f.jobSpec.id, Promise[JobSpec])
    service ! DeleteJobSpec(f.jobSpec.id, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(JobSpecChangeInFlight(f.jobSpec.id))
    eventually(service.underlyingActor.inFlightChanges should have size 1)
  }

  test("A jobSpec can not be deleted, if the repository fails") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val promise = Promise[JobSpec]
    val exception = new RuntimeException("failed")
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is created")
    service ! DeleteJobSpec(f.jobSpec.id, promise)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! PersistFailed(self, f.jobSpec.id, exception, promise)

    Then("An exception will be thrown")
    promise.future.failed.futureValue should be(exception)
    eventually(service.underlyingActor.inFlightChanges should have size 0)
    service.underlyingActor.allJobs should have size 1
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val repository = new InMemoryRepository[PathId, JobSpec]
    val dummy = TestActors.echoActorProps
    val dummyQueue = new LinkedBlockingDeque[TestActor.Message]()
    val dummyProp = Props(new TestActor(dummyQueue))
    val jobSpec = JobSpec(PathId("/test"), "test")
    val jobSpecService = TestActorRef[JobSpecServiceActor](JobSpecServiceActor.props(repository, (id: PathId) => dummyProp, _ => dummyProp))
  }
}

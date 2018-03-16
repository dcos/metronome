package dcos.metronome
package jobspec.impl

import java.io.IOException
import java.util.concurrent.LinkedBlockingDeque

import akka.actor.{ Actor, ActorSystem, Props, Status }
import akka.testkit._
import akka.pattern.ask
import akka.util.Timeout
import dcos.metronome.measurement.MethodMeasurementFixture
import dcos.metronome.{ JobSpecAlreadyExists, JobSpecChangeInFlight, JobSpecDoesNotExist }
import dcos.metronome.model.{ CronSpec, JobId, JobSpec, ScheduleSpec }
import dcos.metronome.repository.impl.InMemoryRepository
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, FunSuiteLike, GivenWhenThen, Matchers }

import scala.concurrent.duration._

class JobSpecServiceActorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with Matchers with Eventually with ImplicitSender {

  import JobSpecServiceActor._
  import JobSpecPersistenceActor._

  test("An existing jobSpec can be retrieved") {
    Given("A service with one jobs")
    val f = new Fixture
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A jobSpec is created")
    val result = service.ask(GetJobSpec(f.jobSpec.id)).mapTo[Option[JobSpec]]

    Then("The jobSpec is available")
    result.futureValue should be(Some(f.jobSpec))
  }

  test("A non existing jobSpec can be retrieved") {
    Given("A service with no jobs")
    val f = new Fixture
    val service = f.jobSpecService

    When("A jobSpec is created")
    val result = service.ask(GetJobSpec(f.jobSpec.id)).mapTo[Option[JobSpec]]

    Then("The jobSpec is available")
    result.futureValue should be(None)
  }

  test("All available jobSpecs can be retrieved") {
    Given("A service with 3 jobs")
    val f = new Fixture
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec.copy(id = JobId("test.one")))
    service.underlyingActor.addJobSpec(f.jobSpec.copy(id = JobId("test.two")))
    service.underlyingActor.addJobSpec(f.jobSpec.copy(id = JobId("test.three")))

    When("A jobSpec is created")
    val result = service.ask(ListJobSpecs(_ => true)).mapTo[Iterable[JobSpec]]

    Then("The jobSpec is available")
    result.futureValue should have size 3
  }

  test("A jobSpec can be created") {
    Given("A service with no jobs")
    val f = new Fixture
    val service = f.jobSpecService

    When("A jobSpec is created")
    service ! CreateJobSpec(f.jobSpec)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! Created(self, f.jobSpec, self)

    Then("The jobSpec is available")
    expectMsg(f.jobSpec)
    service.underlyingActor.allJobs should have size 1
    service.underlyingActor.allJobs(f.jobSpec.id) should be (f.jobSpec)
    service.underlyingActor.inFlightChanges should have size 0
  }

  test("A jobSpec can not be created, if it exists") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is created")
    service ! CreateJobSpec(f.jobSpec)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(JobSpecAlreadyExists(f.jobSpec.id)))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
  }

  test("A jobSpec can not be created, if the repository fails") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val exception = new RuntimeException("failed")

    When("An existing jobSpec is created")
    service ! CreateJobSpec(f.jobSpec)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! PersistFailed(self, f.jobSpec.id, exception, self)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(exception))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
    service.underlyingActor.allJobs should have size 0
  }

  test("A jobSpec can be updated") {
    Given("A service with one jobs")
    val f = new Fixture
    val changed = f.jobSpec.copy(description = Some("changed"))
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A jobSpec is updated")
    service ! UpdateJobSpec(f.jobSpec.id, _ => changed)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! Updated(self, changed, self)

    Then("The jobSpec is updated")
    expectMsg(changed)
    service.underlyingActor.allJobs should have size 1
    service.underlyingActor.allJobs(f.jobSpec.id) should be (changed)
    service.underlyingActor.inFlightChanges should have size 0
  }

  test("A jobSpec can not be updated, if it exists in the actor") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService

    When("A non existing jobSpec is updated")
    service ! UpdateJobSpec(f.jobSpec.id, identity)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(JobSpecDoesNotExist(f.jobSpec.id)))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
  }

  test("A jobSpec can not be updated, if there is a change in flight") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is updated twice")
    service ! UpdateJobSpec(f.jobSpec.id, identity)
    service ! UpdateJobSpec(f.jobSpec.id, identity)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(JobSpecChangeInFlight(f.jobSpec.id)))
    eventually(service.underlyingActor.inFlightChanges should have size 1)
  }

  test("A jobSpec can not be updated, if the repository fails") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val exception = new RuntimeException("failed")
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is updated and the repo fails")
    service ! UpdateJobSpec(f.jobSpec.id, identity)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! PersistFailed(self, f.jobSpec.id, exception, self)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(exception))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
    service.underlyingActor.allJobs should have size 1
  }

  test("A jobSpec can be deleted") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A jobSpec is created")
    service ! DeleteJobSpec(f.jobSpec.id)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! Deleted(self, f.jobSpec, self)

    Then("The jobSpec is available")
    expectMsg(f.jobSpec)
    service.underlyingActor.allJobs should have size 0
    service.underlyingActor.inFlightChanges should have size 0
  }

  test("A jobSpec can not be deleted, if it does not exist") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService

    When("A change is already in flight")
    service ! DeleteJobSpec(f.jobSpec.id)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(JobSpecDoesNotExist(f.jobSpec.id)))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
  }

  test("A jobSpec can not be deleted, if there is a change in flight") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("A change is already in flight")
    service ! DeleteJobSpec(f.jobSpec.id)
    service ! DeleteJobSpec(f.jobSpec.id)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(JobSpecChangeInFlight(f.jobSpec.id)))
    eventually(service.underlyingActor.inFlightChanges should have size 1)
  }

  test("A jobSpec can not be deleted, if the repository fails") {
    Given("A service with one job")
    val f = new Fixture
    val service = f.jobSpecService
    val exception = new RuntimeException("failed")
    service.underlyingActor.addJobSpec(f.jobSpec)

    When("An existing jobSpec is created")
    service ! DeleteJobSpec(f.jobSpec.id)
    eventually(service.underlyingActor.inFlightChanges should have size 1)
    service ! PersistFailed(self, f.jobSpec.id, exception, self)

    Then("An exception will be thrown")
    expectMsg(Status.Failure(exception))
    eventually(service.underlyingActor.inFlightChanges should have size 0)
    service.underlyingActor.allJobs should have size 1
  }

  test("A disabled jobSpec will not be started") {
    Given("A disabled jobSpec")
    val f = new Fixture
    val disabledJobSpec = JobSpec(JobId("disabled"), schedules = Seq(ScheduleSpec(id = "minutely", cron = CronSpec("*/1 * * * *"), enabled = false)))
    val service = f.jobSpecService

    When("A disabled job is added")
    val maybeRef = service.underlyingActor.addJobSpec(disabledJobSpec)

    Then("It does not create a JobSpecSchedulerActor")
    maybeRef should be(None)
  }

  implicit val timeout: Timeout = 3.seconds

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val repository = new InMemoryRepository[JobId, JobSpec]
    val dummyQueue = new LinkedBlockingDeque[TestActor.Message]()
    val dummyProp = Props(new TestActor(dummyQueue))
    val jobSpec = JobSpec(JobId("test"))
    val jobSpecService = TestActorRef[JobSpecServiceActor](JobSpecServiceActor.props(repository, (id: JobId) => dummyProp, _ => dummyProp, MethodMeasurementFixture.empty))
  }
}

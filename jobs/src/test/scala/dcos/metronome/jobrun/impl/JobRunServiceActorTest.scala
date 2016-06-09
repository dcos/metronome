package dcos.metronome.jobrun.impl

import java.util.concurrent.LinkedBlockingDeque

import akka.actor.{ ActorSystem, Props }
import akka.testkit._
import dcos.metronome.behavior.BehaviorFixture
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.jobrun.impl.JobRunExecutorActor.{ JobRunAborted, JobRunFinished }
import dcos.metronome.jobrun.impl.JobRunServiceActor._
import dcos.metronome.model._
import dcos.metronome.repository.impl.InMemoryRepository
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import mesosphere.marathon.state.PathId
import org.joda.time.DateTime
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

import scala.concurrent.{ Future, Promise }

class JobRunServiceActorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with Matchers with Eventually with ImplicitSender with Mockito {

  test("List runs will list all running services") {
    Given("A service with 2 jobRuns")
    val f = new Fixture
    val actor = f.serviceActor
    actor.underlyingActor.allJobRuns += f.run1.jobRun.id -> f.run1
    actor.underlyingActor.allJobRuns += f.run2.jobRun.id -> f.run2
    val promise = Promise[Iterable[StartedJobRun]]

    When("The list of started job runs is queried")
    actor ! ListRuns(promise)

    Then("The list of started job runs is returned")
    promise.future.futureValue should have size 2
    promise.future.futureValue should contain(f.run1)
    promise.future.futureValue should contain(f.run2)
    system.stop(actor)
  }

  test("Get a specific job") {
    Given("A service with 2 jobRuns")
    val f = new Fixture
    val actor = f.serviceActor
    actor.underlyingActor.allJobRuns += f.run1.jobRun.id -> f.run1
    actor.underlyingActor.allJobRuns += f.run2.jobRun.id -> f.run2

    When("An existing jobRun is queried")
    val existing = Promise[Option[StartedJobRun]]
    actor ! GetJobRun(f.run1.jobRun.id, existing)

    Then("The job run is returned")
    existing.future.futureValue should be (defined)
    existing.future.futureValue.get should be(f.run1)

    When("A non existing jobRun is queried")
    val nonExisting = Promise[Option[StartedJobRun]]
    actor ! GetJobRun(JobRunId(f.jobSpec), nonExisting)

    Then("None is returned")
    nonExisting.future.futureValue should be (empty)
    system.stop(actor)
  }

  test("Get all jobRuns for one spec") {
    Given("A service with 2 jobRuns")
    val f = new Fixture
    val actor = f.serviceActor
    actor.underlyingActor.allJobRuns += f.run1.jobRun.id -> f.run1
    actor.underlyingActor.allJobRuns += f.run2.jobRun.id -> f.run2

    When("An existing jobRun is queried")
    val existing = Promise[Iterable[StartedJobRun]]
    actor ! GetActiveJobRuns(f.jobSpec.id, existing)

    Then("The list of started job runs is returned")
    existing.future.futureValue should have size 2

    When("A non existing jobRun is queried")
    val nonExisting = Promise[Iterable[StartedJobRun]]
    actor ! GetActiveJobRuns(PathId("n/a"), nonExisting)

    Then("An empty list is returned")
    nonExisting.future.futureValue should be (empty)
    system.stop(actor)
  }

  test("Triggering a jobRun works") {
    Given("An empty service")
    val f = new Fixture
    val actor = f.serviceActor

    When("An existing jobRun is queried")
    val promise = Promise[StartedJobRun]
    actor ! TriggerJobRun(f.jobSpec, promise)

    Then("The list of started job runs is returned")
    val started = promise.future.futureValue
    started.jobRun.jobSpec should be(f.jobSpec)
    actor.underlyingActor.allJobRuns should have size 1
    system.stop(actor)
  }

  test("A finished job run will be removed from the registry") {
    Given("An empty service")
    val f = new Fixture
    val actor = f.serviceActor
    val promise = Promise[StartedJobRun]
    actor ! TriggerJobRun(f.jobSpec, promise)
    val startedRun = promise.future.futureValue

    When("The job finished")
    val result = JobResult(startedRun.jobRun)
    actor ! JobRunFinished(result)

    Then("The list of started job runs is returned")
    eventually(actor.underlyingActor.allJobRuns should have size 0)
    eventually(actor.underlyingActor.allRunExecutors should have size 0)
  }

  test("A failed job run will be removed from the registry") {
    Given("An empty service")
    val f = new Fixture
    val actor = f.serviceActor
    val promise = Promise[StartedJobRun]
    actor ! TriggerJobRun(f.jobSpec, promise)
    val startedRun = promise.future.futureValue

    When("The job finished")
    val result = JobResult(startedRun.jobRun)
    actor ! JobRunAborted(result)

    Then("The list of started job runs is returned")
    eventually(actor.underlyingActor.allJobRuns should have size 0)
    eventually(actor.underlyingActor.allRunExecutors should have size 0)
  }

  test("Kill Job Run works") {
    Given("A service with 2 jobRuns")
    val f = new Fixture
    val actor = f.serviceActor
    val promise = Promise[StartedJobRun]
    actor ! TriggerJobRun(f.jobSpec, promise)
    val startedRun = promise.future.futureValue

    When("An existing jobRun is queried")
    val kill = Promise[StartedJobRun]
    actor ! KillJobRun(startedRun.jobRun.id, kill)

    Then("The list of started job runs is returned")
    kill.future.futureValue should be(startedRun)

    And("The executor sends a task aborted")
    val result = JobResult(startedRun.jobRun)
    actor ! JobRunAborted(result)

    Then("The list of started job runs is returned")
    eventually(actor.underlyingActor.allJobRuns should have size 0)
    eventually(actor.underlyingActor.allRunExecutors should have size 0)
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val id = PathId("/test")
    val jobSpec = JobSpec(id, "test")
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))

    def run() = {
      val jobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Active, clock.now(), None, Seq.empty)
      new StartedJobRun(jobRun, Future.successful(JobResult(jobRun)))
    }
    val run1 = run()
    val run2 = run()

    val dummyQueue = new LinkedBlockingDeque[TestActor.Message]()
    val dummyProp = Props(new TestActor(dummyQueue))
    val repo = new InMemoryRepository[JobRunId, JobRun]
    val behavior = BehaviorFixture.empty

    var createExecutor: (JobRun, Promise[JobResult]) => Props = (_, _) => dummyProp
    def serviceActor = TestActorRef[JobRunServiceActor](JobRunServiceActor.props(clock, createExecutor, repo, behavior))
  }
}

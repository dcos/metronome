package dcos.metronome.jobspec.impl

import java.time.{Clock, LocalDateTime, ZoneOffset}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import dcos.metronome.{Seq, SettableClock}
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.{Event, JobId, JobRun, JobRunId, JobRunStatus, JobSpec}
import dcos.metronome.utils.test.Mockito
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, GivenWhenThen, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}

class JobSpecDependencyActorTest
    extends TestKit(ActorSystem("test"))
    with FunSuiteLike
    with BeforeAndAfterAll
    with GivenWhenThen
    with ScalaFutures
    with Matchers
    with Eventually
    with ImplicitSender
    with Mockito {

  test("when job A and B finish job C is triggered") {
    Given("Job C with dependencies on job A and B")
    val f = new Fixture()

    And("A dependency actor for C without a last run")
    val actor = f.dependencyActor

    And("Successful runs for A and B")
    val finished = f.clock.instant().plusSeconds(10)
    val jobARun = JobRun(
      JobRunId(f.jobA.id, "finished"),
      f.jobA,
      JobRunStatus.Success,
      f.clock.instant(),
      Some(finished),
      None,
      Map.empty
    )
    val jobBRun = JobRun(
      JobRunId(f.jobB.id, "finished"),
      f.jobB,
      JobRunStatus.Success,
      f.clock.instant(),
      Some(finished),
      None,
      Map.empty
    )

    When("Job A and B finish")
    actor ! Event.JobRunFinished(jobARun)
    actor ! Event.JobRunFinished(jobBRun)

    Then("Job C is triggered")
    verify(f.jobRunService, timeout(1000).times(1)).startJobRun(f.jobC)
    system.stop(actor)
  }

  test("when job A finished twice and B once C is triggered after B") {
    Given("Job C with dependencies on job A and B")
    val f = new Fixture()

    And("A dependency actor for C without a last run")
    val actor = f.dependencyActor

    And("A already finished once")
    val firstTime = f.clock.instant().plusSeconds(10)
    val jobARun1 = JobRun(
      JobRunId(f.jobA.id, "finished"),
      f.jobA,
      JobRunStatus.Success,
      f.clock.instant(),
      Some(firstTime),
      None,
      Map.empty
    )
    actor ! Event.JobRunFinished(jobARun1)

    When("Job A finishes the second time and B finish its first time")
    val secondTime = f.clock.instant().plusSeconds(20)
    val jobARun2 = JobRun(
      JobRunId(f.jobA.id, "finished"),
      f.jobA,
      JobRunStatus.Success,
      f.clock.instant(),
      Some(secondTime),
      None,
      Map.empty
    )
    val jobBRun = JobRun(
      JobRunId(f.jobB.id, "finished"),
      f.jobB,
      JobRunStatus.Success,
      f.clock.instant(),
      Some(secondTime),
      None,
      Map.empty
    )
    actor ! Event.JobRunFinished(jobARun2)
    actor ! Event.JobRunFinished(jobBRun)

    Then("Job C is triggered only once")
    verify(f.jobRunService, timeout(1000).times(1)).startJobRun(f.jobC)
    system.stop(actor)
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    // 01:59am CDT 2017-11-05 was end of daylight saving time
    val clock = new SettableClock(
      Clock.fixed(LocalDateTime.parse("2018-01-13T13:59").toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    )

    val jobA = JobSpec(JobId("a"))
    val jobB = JobSpec(JobId("b"))
    val jobC = JobSpec(JobId("c"), dependencies = Seq(jobA.id, jobB.id))
    val jobRunService = mock[JobRunService]
    def dependencyActor = TestActorRef[JobSpecDependencyActor](JobSpecDependencyActor.props(jobC, jobRunService))
  }
}

package dcos.metronome.jobspec.impl

import akka.actor.ActorSystem
import akka.testkit.{ TestActorRef, ImplicitSender, TestKit }
import dcos.metronome.model.{ CronSpec, ScheduleSpec, JobSpec }
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import mesosphere.marathon.state.PathId
import org.joda.time.DateTime
import org.scalatest.concurrent.{ ScalaFutures, Eventually }
import org.scalatest._

class JobSpecSchedulerActorTest extends TestKit(ActorSystem("test")) with FunSuiteLike with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with Matchers with Eventually with ImplicitSender with Mockito {

  import JobSpecSchedulerActor._

  test("Creating an JobScheduleActor will trigger a reschedule") {
    Given("A job scheduling actor")
    val f = new Fixture
    val nextRun = DateTime.parse("2016-06-01T08:51:00.000Z")

    When("The actor is created")
    val actor = f.scheduleActor

    Then("The next run is scheduled")
    eventually(actor.underlyingActor.scheduledAt should be(Some(nextRun)))
    actor.underlyingActor.nextSchedule should be (defined)
    system.stop(actor)
  }

  test("Updating an JobScheduleActor will trigger a reschedule") {
    Given("A job scheduling actor")
    val f = new Fixture
    val actor = f.scheduleActor
    val update = f.jobSpec.copy(schedule = Some(ScheduleSpec(cron = f.everyHourHalfPast)))
    eventually(actor.underlyingActor.nextSchedule should be(defined))
    val cancelable = actor.underlyingActor.nextSchedule.get
    val nextRun = DateTime.parse("2016-06-01T09:30:00.000Z")

    When("The actor is created")
    actor ! UpdateJobSpec(update)

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.scheduledAt should be(Some(nextRun)))
    actor.underlyingActor.nextSchedule should be (defined)
    actor.underlyingActor.spec should be (update)
    cancelable.isCancelled should be (true)
    system.stop(actor)
  }

  test("If the next scheduled time has reached, a new job run is triggerd") {
    Given("A job scheduling actor")
    val f = new Fixture
    val actor = f.scheduleActor
    val nextRun = DateTime.parse("2016-06-01T08:52:00.000Z")

    When("The actor is created")
    actor ! StartJob

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.scheduledAt should be(Some(nextRun)))
    actor.underlyingActor.nextSchedule should be (defined)
    system.stop(actor)
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val CronSpec(everyMinute) = "* * * * *"
    val CronSpec(everyHourHalfPast) = "30 * * * *"
    val id = PathId("/test")
    val jobSpec = JobSpec(id, "test").copy(schedule = Some(ScheduleSpec(cron = everyMinute)))
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))
    def scheduleActor = TestActorRef[JobSpecSchedulerActor](JobSpecSchedulerActor.props(jobSpec, clock))
  }
}

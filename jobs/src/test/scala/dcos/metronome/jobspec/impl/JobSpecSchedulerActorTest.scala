package dcos.metronome
package jobspec.impl

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import dcos.metronome.behavior.BehaviorFixture
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model._
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

import scala.collection.immutable._

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
    val update = f.jobSpec.copy(schedules = Seq(ScheduleSpec("everyHour", cron = f.everyHourHalfPast)))
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

  test("If the next scheduled time has reached, a new job run is triggered") {
    Given("A job scheduling actor")
    val f = new Fixture
    val actor = f.scheduleActor
    val nextRun = DateTime.parse("2016-06-01T08:52:00.000Z")

    When("The actor is created")
    actor ! StartJob(f.jobSpec.schedules.head)

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.scheduledAt should be(Some(nextRun)))
    actor.underlyingActor.nextSchedule should be (defined)
    system.stop(actor)
  }

  test("If the next scheduled time has reached AND FORBID, a new job run is NOT triggered however a new time is scheduled") {
    Given("A job scheduling actor")
    val f = new Fixture
    val actor = f.scheduleActor
    val nextRun = DateTime.parse("2016-06-01T08:52:00.000Z")

    When("The actor is created")
    actor ! StartJob(f.jobSpec.schedules(1))

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.scheduledAt should be(Some(nextRun)))
    actor.underlyingActor.nextSchedule should be (defined)
    system.stop(actor)
  }

  test("If the next scheduled time is on a TZ boundary") {
    Given("A job scheduling actor")
    val f = new DaylightSavingFixture
    val actor = f.scheduleActor
    // this is 1:01am CST
    val nextRun = DateTime.parse("2018-01-13T14:01:00.000Z")

    When("The actor is created")
    actor ! StartJob(f.jobSpec.schedules.head)

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
    val id = JobId("/test")
    val jobSpec = JobSpec(id).copy(schedules = Seq(
      ScheduleSpec("every_minute", cron = everyMinute),
      ScheduleSpec("minutely", cron = everyMinute, concurrencyPolicy = ConcurrencyPolicy.Forbid)))
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))
    val behavior = BehaviorFixture.empty
    val jobRunService = mock[JobRunService]
    def scheduleActor = TestActorRef[JobSpecSchedulerActor](JobSpecSchedulerActor.props(jobSpec, clock, jobRunService, behavior))
  }

  class DaylightSavingFixture {
    val CronSpec(everyMinute) = "* * * * *"
    val id = JobId("/daylight")
    val jobSpec = JobSpec(id).copy(schedules = Seq(ScheduleSpec("every_minute", cron = everyMinute, timeZone = DateTimeZone.forID("Pacific/Fiji"))))
    // 01:59am CDT 2017-11-05 was end of daylight saving time
    val clock = new FixedClock(DateTime.parse("2018-01-13T13:59Z"))
    val behavior = BehaviorFixture.empty
    val jobRunService = mock[JobRunService]
    def scheduleActor = TestActorRef[JobSpecSchedulerActor](JobSpecSchedulerActor.props(jobSpec, clock, jobRunService, behavior))
  }
}

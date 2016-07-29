package dcos.metronome.jobspec.impl

import akka.actor.ActorSystem
import akka.testkit.{ TestActorRef, ImplicitSender, TestKit }
import dcos.metronome.behavior.BehaviorFixture
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.model.{ JobId, CronSpec, JobSpec, ScheduleSpec }
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import org.joda.time.DateTime
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
    eventually(actor.underlyingActor.schedules.get(f.scheduleSpec).map(_.scheduledAt) should be(Some(nextRun)))
    actor.underlyingActor.schedules.get(f.scheduleSpec) should be (defined)
    system.stop(actor)
  }

  test("Updating an JobScheduleActor will trigger a reschedule") {
    Given("A job scheduling actor")
    val f = new Fixture
    val actor = f.scheduleActor
    val newScheduleSpec = ScheduleSpec("everyHour", cron = f.everyHourHalfPast)
    val update = f.jobSpec.copy(schedules = Seq(newScheduleSpec))
    eventually(actor.underlyingActor.schedules.get(f.scheduleSpec) should be(defined))
    val cancelable = actor.underlyingActor.schedules.get(f.scheduleSpec).get.cancellable
    val nextRun = DateTime.parse("2016-06-01T09:30:00.000Z")

    When("The actor is created")
    actor ! UpdateJobSpec(update)

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.schedules.get(newScheduleSpec).map(_.scheduledAt) should be(Some(nextRun)))
    actor.underlyingActor.schedules.get(newScheduleSpec) should be (defined)
    actor.underlyingActor.spec should be (update)
    cancelable.isCancelled should be (true)
    system.stop(actor)
  }

  test("Updating an JobScheduleActor with multiple schedules (add) will trigger a reschedule") {
    Given("A job scheduling actor")
    val f = new Fixture
    val actor = f.scheduleActor
    val additionalScheduleSpec = ScheduleSpec("everyHalfHourPast", cron = f.everyHourHalfPast)
    val update = f.jobSpec.copy(schedules = Seq(f.scheduleSpec, additionalScheduleSpec))
    eventually(actor.underlyingActor.schedules.get(f.scheduleSpec) should be(defined))

    val cancelable = actor.underlyingActor.schedules.get(f.scheduleSpec).get.cancellable
    val nextRunForExistingSchedule = DateTime.parse("2016-06-01T08:51:00.000Z")
    val nextRunForAddedSchedule = DateTime.parse("2016-06-01T09:30:00.000Z")

    When("The actor is created")
    actor ! UpdateJobSpec(update)

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.schedules.get(f.scheduleSpec).map(_.scheduledAt) should be(Some(nextRunForExistingSchedule)))
    eventually(actor.underlyingActor.schedules.get(additionalScheduleSpec).map(_.scheduledAt) should be(Some(nextRunForAddedSchedule)))
    actor.underlyingActor.schedules.get(additionalScheduleSpec) should be (defined)
    actor.underlyingActor.spec should be (update)
    cancelable.isCancelled should be (false)
    system.stop(actor)
  }

  test("Updating an JobScheduleActor with multiple schedules (remove) will trigger a reschedule") {
    Given("A job scheduling actor")
    val f = new Fixture
    val firstScheduleSpec = ScheduleSpec("everyHalfHourPast-First", cron = f.everyHourHalfPast)
    val secondScheduleSpec = ScheduleSpec("everyHalfHourPast-Second", cron = f.everyHourHalfPast)
    val actor = f.scheduleActor(f.jobSpec.copy(schedules = Seq(firstScheduleSpec, secondScheduleSpec)))

    val update = f.jobSpec.copy(schedules = Seq(firstScheduleSpec))
    eventually(actor.underlyingActor.schedules.get(secondScheduleSpec) should be(defined))

    val cancelable = actor.underlyingActor.schedules.get(secondScheduleSpec).get.cancellable
    val nextRunForFirstSchedule = DateTime.parse("2016-06-01T09:30:00.000Z")

    When("The actor is created")
    actor ! UpdateJobSpec(update)

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.schedules.get(firstScheduleSpec).map(_.scheduledAt) should be(Some(nextRunForFirstSchedule)))
    eventually(actor.underlyingActor.schedules.get(secondScheduleSpec).map(_.scheduledAt) should be(None))
    actor.underlyingActor.schedules.get(firstScheduleSpec) should be (defined)
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
    actor ! StartJob(f.scheduleSpec)

    Then("The next run is rescheduled")
    eventually(actor.underlyingActor.schedules.get(f.scheduleSpec).map(_.scheduledAt) should be(Some(nextRun)))
    actor.underlyingActor.schedules.get(f.scheduleSpec) should be (defined)
    system.stop(actor)
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val CronSpec(everyMinute) = "* * * * *"
    val CronSpec(everyHourHalfPast) = "30 * * * *"
    val id = JobId("/test")
    val scheduleSpec = ScheduleSpec("every_minute", cron = everyMinute)
    val jobSpec = JobSpec(id).copy(schedules = Seq(scheduleSpec))
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))
    val behavior = BehaviorFixture.empty
    val jobRunService = mock[JobRunService]
    def scheduleActor = TestActorRef[JobSpecSchedulerActor](JobSpecSchedulerActor.props(jobSpec, clock, jobRunService, behavior))
    def scheduleActor(jSpec: JobSpec) = TestActorRef[JobSpecSchedulerActor](JobSpecSchedulerActor.props(jSpec, clock, jobRunService, behavior))
  }
}

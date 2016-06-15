package dcos.metronome.jobrun.impl

import akka.actor.{ ActorContext, ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import dcos.metronome.JobRunFailed
import dcos.metronome.behavior.BehaviorFixture
import dcos.metronome.jobrun.impl.JobRunExecutorActor.ForwardStatusUpdate
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus, JobSpec }
import dcos.metronome.utils.glue.MarathonImplicits.RunSpecId
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.event.MesosStatusUpdateEvent
import mesosphere.marathon.state.PathId
import org.apache.mesos
import org.apache.mesos.SchedulerDriver
import org.joda.time.DateTime
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, FunSuiteLike, GivenWhenThen, Matchers }

import scala.collection.immutable.Seq
import scala.concurrent.Promise
import scala.concurrent.duration._

class JobRunExecutorActorTest extends TestKit(ActorSystem("test"))
    with FunSuiteLike
    with BeforeAndAfterAll
    with GivenWhenThen
    with ScalaFutures
    with Matchers
    with Eventually
    with ImplicitSender
    with Mockito {

  test("ForwardStatusUpdate STAGING with subsequent RUNNING") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, _) = f.initializeStartedExecutorActor()

    When("The actor receives a status update indicating the run is active")
    actor ! f.statusUpdate(mesos.Protos.TaskState.TASK_STAGING)

    Then("The updated JobRun is persisted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]

    And("The JobRun is reported active")
    val msg1 = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    msg1.startedJobRun.jobRun.status shouldBe JobRunStatus.Active

    When("A subsequent RUNNING update is processed")
    actor ! f.statusUpdate(mesos.Protos.TaskState.TASK_RUNNING)

    Then("Nothing is persisted because the JobRunStatus is still Active")
    f.persistenceActor.expectNoMsg(500.millis)

    And("No additional message is send because the job is still active")
    f.parent.expectNoMsg(500.millis)

    system.stop(actor)
  }

  test("ForwardStatusUpdate FINISHED") {
    Given("An executor with a JobRun in state Active")
    val f = new Fixture
    val (actor, activeJobRun) = f.initializeActiveExecutorActor()

    When("The actor receives a status update indicating the run is finished")
    val statusUpdate = f.statusUpdate(mesos.Protos.TaskState.TASK_FINISHED)
    actor ! statusUpdate

    Then("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(activeJobRun.id))

    And("The JobRun deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun update is reported")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Success
    updateMsg.startedJobRun.jobRun.finishedAt shouldEqual Some(DateTime.parse(statusUpdate.update.timestamp))

    And("The JobRun is reported successful")
    val finishedMsg = f.parent.expectMsgType[JobRunExecutorActor.Finished]
    finishedMsg.jobResult.jobRun.status shouldBe JobRunStatus.Success

    And("the promise is completed")
    f.promise.isCompleted

    system.stop(actor)
  }

  test("ForwardStatusUpdate FAILED") {
    Given("An executor with a JobRun in state Active")
    val f = new Fixture
    val (actor, activeJobRun) = f.initializeActiveExecutorActor()

    When("The actor receives a status update TASK_FAILED")
    actor ! f.statusUpdate(mesos.Protos.TaskState.TASK_FAILED)

    Then("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(activeJobRun.id))

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun is reported failed")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Failed]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise is completed")
    f.promise.isCompleted

    system.stop(actor)
  }

  test("KillCurrentJobRun") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, activeJobRun) = f.initializeStartedExecutorActor()

    When("The actor receives a KillCurrentJobRun")
    actor ! JobRunExecutorActor.KillCurrentJobRun

    Then("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(activeJobRun.id))

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun is reported failed")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed

    And("The JobRun is reported aborted")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Aborted]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise is completed")
    f.promise.isCompleted

    system.stop(actor)

  }

  test("Persistence failure is propagated during creating") {
    Given("An executor with a JobRun in state Creating")
    val f = new Fixture
    val (actor, jobRun) = f.initializeCreatingExecutorActor()

    When("The persisting the jobRun fails")
    val exception = new RuntimeException("Create failed")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    f.persistenceActor.send(actor, JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, exception, ()))

    Then("No task is killed because we didn't start one yet")
    noMoreInteractions(f.driver)

    And("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(jobRun.id))

    And("The JobRun is reported failed")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, jobRun, ()))

    And("The JobRun is reported aborted")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Aborted]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise fails")
    f.promise.future.failed.futureValue shouldEqual JobRunFailed(JobResult(jobRun.copy(status = JobRunStatus.Failed)))

    system.stop(actor)
  }

  test("Persistence failure is propagated during starting") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, jobRun) = f.initializeStartedExecutorActor()

    When("The actor receives a status update indicating the run is running")
    val statusUpdate = f.statusUpdate(mesos.Protos.TaskState.TASK_RUNNING)
    actor ! statusUpdate

    And("The JobRun is reported active")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    val taskId = updateMsg.startedJobRun.jobRun.tasks.keys.head

    When("persisting the update fails")
    val exception = new RuntimeException("Create failed")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
    f.persistenceActor.reply(JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, exception, ()))

    Then("The task is killed")
    verify(f.driver).killTask(taskId.mesosTaskId)

    And("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(jobRun.id))

    And("The jobRun is reported failed")
    val secondUpdateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    secondUpdateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, jobRun, ()))

    And("The JobRun is reported aborted")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Aborted]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise fails")
    f.promise.future.failed.futureValue.getMessage shouldEqual JobRunFailed(JobResult(jobRun.copy(status = JobRunStatus.Failed))).getMessage

    system.stop(actor)
  }

  test("Persistence failure is propagated during active") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, jobRun) = f.initializeActiveExecutorActor()

    When("The actor receives a status update indicating the run is finished")
    val statusUpdate = f.statusUpdate(mesos.Protos.TaskState.TASK_FINISHED)
    actor ! statusUpdate

    And("The JobRun is reported successful")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Success
    updateMsg.startedJobRun.jobRun.tasks should have size 1

    When("persisting the update fails")
    val exception = new RuntimeException("Create failed")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, exception, ()))

    Then("No task is killed because it finished")
    noMoreInteractions(f.driver)

    And("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(jobRun.id))

    And("The JobRun is reported aborted")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Aborted]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise fails")
    f.promise.future.failed.futureValue.getMessage shouldEqual JobRunFailed(JobResult(jobRun.copy(status = JobRunStatus.Failed))).getMessage

    system.stop(actor)
  }

  test("Persistence failure is propagated during starting and deleting the jobRun also fails") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, jobRun) = f.initializeStartedExecutorActor()

    When("The actor receives a status update indicating the run is finished")
    val statusUpdate = f.statusUpdate(mesos.Protos.TaskState.TASK_RUNNING)
    actor ! statusUpdate

    And("The JobRun is reported active")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    val taskId = updateMsg.startedJobRun.jobRun.tasks.keys.head

    When("persisting the update fails")
    val exception = new RuntimeException("Create failed")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
    f.persistenceActor.reply(JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, exception, ()))

    Then("The task is killed")
    verify(f.driver).killTask(taskId.mesosTaskId)

    And("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(jobRun.id))

    And("The jobRun is reported failed")
    val secondUpdateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    secondUpdateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, exception, ()))

    And("The JobRun is reported aborted")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Aborted]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise fails")
    f.promise.future.failed.futureValue.getMessage shouldEqual JobRunFailed(JobResult(jobRun.copy(status = JobRunStatus.Failed))).getMessage

    system.stop(actor)
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val id = PathId("/test")
    val jobSpec = JobSpec(id, Some("test"))
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))
    val launchQueue: LaunchQueue = mock[LaunchQueue]
    val driver = mock[SchedulerDriver]
    val driverHolder: MarathonSchedulerDriverHolder = {
      val holder = new MarathonSchedulerDriverHolder
      holder.driver = Some(driver)
      holder
    }

    def statusUpdate(state: mesos.Protos.TaskState) = ForwardStatusUpdate(MesosStatusUpdateEvent(
      "slaveId", Task.Id("taskId"), state.toString, "message", jobSpec.id, "host",
      ipAddresses = None, ports = Seq.empty[Int], "version", "eventType", DateTime.now().toString
    ))

    val persistenceActor = TestProbe()
    val persistenceActorFactory: (JobRunId, ActorContext) => ActorRef = (_, context) => persistenceActor.ref
    val promise: Promise[JobResult] = Promise[JobResult]
    val parent = TestProbe()
    val behaviour = BehaviorFixture.empty
    def executorActor(jobRun: JobRun) = {
      TestActorRef(JobRunExecutorActor.props(jobRun, promise, persistenceActorFactory, launchQueue, driverHolder, clock, behaviour), parent.ref, "JobRunExecutor")
    }

    def initializeCreatingExecutorActor() = {
      val startingJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Starting, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      (actorRef, startingJobRun)
    }

    def initializeStartedExecutorActor() = {
      val startingJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Starting, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit))
      verify(launchQueue, timeout(1000)).add(any, any)
      (actorRef, startingJobRun)
    }

    def initializeActiveExecutorActor() = {
      val activeJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(activeJobRun)
      (actorRef, activeJobRun)
    }
  }
}

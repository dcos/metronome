package dcos.metronome.jobrun.impl

import akka.actor.{ ActorContext, ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import dcos.metronome.JobRunFailed
import dcos.metronome.behavior.BehaviorFixture
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.impl.JobRunExecutorActor.ForwardStatusUpdate
import dcos.metronome.model.{ JobResult, JobRun, JobRunId, JobRunStatus, JobSpec }
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.glue.MarathonImplicits
import dcos.metronome.utils.glue.MarathonImplicits.RunSpecId
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedTaskInfo
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.tracker.TaskTracker
import mesosphere.marathon.state.{ PathId, RunSpec, Timestamp }
import org.apache.mesos.SchedulerDriver
import org.apache.mesos
import org.joda.time.DateTime
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, FunSuiteLike, GivenWhenThen, Matchers }

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
    val (actor, _) = f.setupInitialExecutorActor()

    When("The actor receives a status update indicating the run is active")
    actor ! f.statusUpdate(TaskState.Staging)

    Then("The updated JobRun is persisted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]

    And("The JobRun is reported active")
    val msg1 = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    msg1.startedJobRun.jobRun.status shouldBe JobRunStatus.Active

    When("A subsequent RUNNING update is processed")
    actor ! f.statusUpdate(TaskState.Running)

    Then("Nothing is persisted because the JobRunStatus is still Active")
    f.persistenceActor.expectNoMsg(500.millis)

    And("No additional message is send because the job is still active")
    f.parent.expectNoMsg(500.millis)

    system.stop(actor)
  }

  test("ForwardStatusUpdate FINISHED") {
    Given("An executor with a JobRun in state Active")
    val f = new Fixture
    val (actor, activeJobRun) = f.setupActiveExecutorActor()

    When("The actor receives a status update indicating the run is finished")
    val statusUpdate = f.statusUpdate(TaskState.Finished)
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
    updateMsg.startedJobRun.jobRun.finishedAt shouldEqual Some(statusUpdate.update.timestamp)

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
    val (actor, activeJobRun) = f.setupActiveExecutorActor()

    When("The actor receives a status update TASK_FAILED")
    val statusUpdate = f.statusUpdate(TaskState.Failed)
    actor ! statusUpdate

    Then("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(activeJobRun.id))

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun update is reported")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed
    updateMsg.startedJobRun.jobRun.finishedAt shouldBe None

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
    val (actor, activeJobRun) = f.setupInitialExecutorActor()

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
    val (actor, jobRun) = f.setupCreatingExecutorActor()

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
    val (actor, jobRun) = f.setupInitialExecutorActor()

    When("The actor receives a status update indicating the run is running")
    val statusUpdate = f.statusUpdate(TaskState.Running)
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
    val (actor, jobRun) = f.setupActiveExecutorActor()

    When("The actor receives a status update indicating the run is finished")
    val statusUpdate = f.statusUpdate(TaskState.Finished)
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
    val (actor, jobRun) = f.setupInitialExecutorActor()

    When("The actor receives a status update indicating the run is finished")
    val statusUpdate = f.statusUpdate(TaskState.Running)
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

  test("Init of JobRun with JobRunStatus.Success") {
    val f = new Fixture
    import f._
    val successfulJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Success, clock.now(), None, Map.empty)
    val actorRef: ActorRef = executorActor(successfulJobRun)

    verify(launchQueue, timeout(1000)).purge(RunSpecId(successfulJobRun.id))
    val parentUpdate = parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    parentUpdate.startedJobRun.jobRun.status shouldBe JobRunStatus.Success
    persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(persistenceActor.ref, successfulJobRun, ()))
    val finishedMsg = parent.expectMsgType[JobRunExecutorActor.Finished]
    finishedMsg.jobResult.jobRun.status shouldBe JobRunStatus.Success
  }

  test("Init of JobRun with JobRunStatus.Failed") {
    val f = new Fixture
    import f._
    val failedJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Failed, clock.now(), None, Map.empty)
    val actorRef: ActorRef = executorActor(failedJobRun)

    verify(launchQueue, timeout(1000)).purge(RunSpecId(failedJobRun.id))
    val parentUpdate = parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    parentUpdate.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed
    persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(persistenceActor.ref, failedJobRun, ()))
    val finishedMsg = parent.expectMsgType[JobRunExecutorActor.Failed]
    finishedMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed
  }

  test("Init of JobRun with JobRunStatus.Active and nonexistent launchQueue") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Active")
    val activeJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
    val runSpecId = RunSpecId(activeJobRun.id)
    f.launchQueue.get(runSpecId) returns None

    When("the actor is initialized")
    val actorRef: ActorRef = executorActor(activeJobRun)

    Then("it will fetch info about queued or running tasks")
    verify(f.launchQueue, timeout(1000)).get(runSpecId)

    And("a task is placed onto the launch queue")
    verify(launchQueue, timeout(1000)).add(any, any)
  }

  test("Init of JobRun with JobRunStatus.Active and EMPTY launchQueue") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Active")
    val activeJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
    val runSpecId = RunSpecId(activeJobRun.id)
    val runSpec: RunSpec = MarathonImplicits.jobRunToRunSpec(activeJobRun)
    val queuedTaskInfo = new QueuedTaskInfo(
      runSpec = runSpec,
      inProgress = false,
      tasksLeftToLaunch = 0,
      finalTaskCount = 0,
      tasksLost = 0,
      backOffUntil = Timestamp(0)
    )
    f.launchQueue.get(runSpecId) returns Some(queuedTaskInfo)

    When("the actor is initialized")
    val actorRef: ActorRef = executorActor(activeJobRun)

    Then("it will fetch info about queued or running tasks")
    verify(f.launchQueue, timeout(1000)).get(runSpecId)

    And("a task is placed onto the launch queue")
    verify(launchQueue, timeout(1000)).add(any, any)
  }

  test("Init of JobRun with JobRunStatus.Active and a task on the launchQueue") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Active")
    val activeJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
    val runSpecId = RunSpecId(activeJobRun.id)
    val runSpec: RunSpec = MarathonImplicits.jobRunToRunSpec(activeJobRun)
    val queuedTaskInfo = new QueuedTaskInfo(
      runSpec = runSpec,
      inProgress = true,
      tasksLeftToLaunch = 0,
      finalTaskCount = 1,
      tasksLost = 0,
      backOffUntil = Timestamp(0)
    )
    launchQueue.get(runSpecId) returns Some(queuedTaskInfo)
    taskTracker.appTasksLaunchedSync(runSpecId) returns Seq(
      mockTask(taskId, Timestamp(clock.now()), mesos.Protos.TaskState.TASK_RUNNING)
    )

    When("the actor is initialized")
    val actorRef: ActorRef = executorActor(activeJobRun)

    Then("it will fetch info about queued or running tasks")
    verify(f.launchQueue, timeout(1000)).get(runSpecId)

    And("NO task is placed onto the launch queue")
    noMoreInteractions(launchQueue)
  }

  // FIXME (urgent): implement test
  ignore("A task does not become active and is killed by the overdueTasksActor") {
    fail("Test not implemented!")
  }

  def mockTask(taskId: Task.Id, stagedAt: Timestamp, mesosState: mesos.Protos.TaskState): Task.LaunchedEphemeral = {
    val status: Task.Status = mock[Task.Status]
    status.stagedAt returns stagedAt
    val mesosStatus: mesos.Protos.TaskStatus = mesos.Protos.TaskStatus.newBuilder()
      .setState(mesosState)
      .buildPartial()
    val task = mock[Task.LaunchedEphemeral]
    task.taskId returns taskId
    task.status returns status
    task.mesosStatus returns Some(mesosStatus)
    task
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val runSpecId = PathId("/test")
    val taskId = Task.Id.forRunSpec(runSpecId)
    val jobSpec = JobSpec(runSpecId, Some("test"))
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))
    val launchQueue: LaunchQueue = mock[LaunchQueue]
    val taskTracker: TaskTracker = mock[TaskTracker]
    val driver = mock[SchedulerDriver]
    val driverHolder: MarathonSchedulerDriverHolder = {
      val holder = new MarathonSchedulerDriverHolder
      holder.driver = Some(driver)
      holder
    }

    def statusUpdate(state: TaskState) = ForwardStatusUpdate(TaskStateChangedEvent(
      taskId = taskId, taskState = state, timestamp = DateTime.now()
    ))

    val persistenceActor = TestProbe()
    val persistenceActorFactory: (JobRunId, ActorContext) => ActorRef = (_, context) => persistenceActor.ref
    val promise: Promise[JobResult] = Promise[JobResult]
    val parent = TestProbe()
    val behaviour = BehaviorFixture.empty
    def executorActor(jobRun: JobRun) = {
      TestActorRef(JobRunExecutorActor.props(jobRun, promise, persistenceActorFactory,
        launchQueue, taskTracker, driverHolder, clock, behaviour), parent.ref, "JobRunExecutor")
    }

    def setupCreatingExecutorActor() = {
      val startingJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Initial, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      (actorRef, startingJobRun)
    }

    def setupInitialExecutorActor() = {
      val startingJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Initial, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit))
      verify(launchQueue, timeout(1000)).add(any, any)
      (actorRef, startingJobRun)
    }

    def setupActiveExecutorActor() = {
      val startingJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Initial, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit))
      verify(launchQueue, timeout(1000)).add(any, any)
      actorRef ! ForwardStatusUpdate(TaskStateChangedEvent(
        taskId = taskId,
        taskState = TaskState.Running,
        timestamp = clock.now()
      ))
      val updateMsg = persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
      persistenceActor.reply(JobRunPersistenceActor.JobRunUpdated(persistenceActor.ref, updateMsg.change(startingJobRun), ()))
      val parentUpdate = parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
      parentUpdate.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
      (actorRef, startingJobRun)
    }
  }
}

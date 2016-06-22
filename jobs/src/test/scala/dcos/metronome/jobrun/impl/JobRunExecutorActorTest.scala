package dcos.metronome.jobrun.impl

import akka.actor.{ ActorContext, ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import dcos.metronome.JobRunFailed
import dcos.metronome.behavior.BehaviorFixture
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.impl.JobRunExecutorActor.ForwardStatusUpdate
import dcos.metronome.model._
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.glue.MarathonImplicits
import dcos.metronome.utils.glue.MarathonImplicits._
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedTaskInfo
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.tracker.TaskTracker
import mesosphere.marathon.state.{ RunSpec, Timestamp }
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
    val (actor, _) = f.setupCreatingExecutorActor()

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
    verify(f.launchQueue).purge(activeJobRun.id.toRunSpecId)

    And("The JobRun deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun update is reported")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Success
    updateMsg.startedJobRun.jobRun.completedAt shouldEqual Some(statusUpdate.update.timestamp)

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
    verify(f.launchQueue).purge(activeJobRun.id.toRunSpecId)

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun update is reported")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed
    updateMsg.startedJobRun.jobRun.completedAt shouldBe Some(f.clock.now())

    And("The JobRun is reported failed")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Failed]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise is completed")
    f.promise.isCompleted

    system.stop(actor)
  }

  // FIXME (urgent): test this in other states as well
  test("KillCurrentJobRun") {
    Given("An executor with a JobRun in state Initial")
    val f = new Fixture
    val (actor, jobRun) = f.setupCreatingExecutorActor()

    When("The actor receives a KillCurrentJobRun")
    actor ! JobRunExecutorActor.KillCurrentJobRun

    Then("The launch queue is purged")
    verify(f.launchQueue).purge(jobRun.id.toRunSpecId)

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, jobRun, ()))

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
    val (actor, jobRun) = f.setupInitialExecutorActor()

    When("The persisting the jobRun fails")
    val exception = new RuntimeException("Create failed")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    f.persistenceActor.send(actor, JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, exception, ()))

    Then("No task is killed because we didn't start one yet")
    noMoreInteractions(f.driver)

    And("The launch queue is purged")
    verify(f.launchQueue).purge(jobRun.id.toRunSpecId)

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
    val (actor, jobRun) = f.setupCreatingExecutorActor()

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
    verify(f.launchQueue).purge(jobRun.id.toRunSpecId)

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
    verify(f.launchQueue).purge(jobRun.id.toRunSpecId)

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
    val (actor, jobRun) = f.setupCreatingExecutorActor()

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
    verify(f.launchQueue).purge(jobRun.id.toRunSpecId)

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
    val successfulJobRun = new JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Success, clock.now(), None, Map.empty)
    val actorRef: ActorRef = executorActor(successfulJobRun)

    verify(launchQueue, timeout(1000)).purge(successfulJobRun.id.toRunSpecId)
    val parentUpdate = parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    parentUpdate.startedJobRun.jobRun.status shouldBe JobRunStatus.Success
    persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(persistenceActor.ref, successfulJobRun, ()))
    val finishedMsg = parent.expectMsgType[JobRunExecutorActor.Finished]
    finishedMsg.jobResult.jobRun.status shouldBe JobRunStatus.Success
  }

  test("Init of JobRun with JobRunStatus.Failed") {
    val f = new Fixture

    Given("a jobRun in status Failed")
    val failedJobRun = new JobRun(JobRunId(f.defaultJobSpec), f.defaultJobSpec, JobRunStatus.Failed, f.clock.now(), None, Map.empty)

    When("an executor is initialized with the failed jobRun")
    f.executorActor(failedJobRun)

    verifyFailureActions(failedJobRun, f)
  }

  test("Init of JobRun with JobRunStatus.Active and nonexistent launchQueue") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Active")
    val activeJobRun = new JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
    val runSpecId = activeJobRun.id.toRunSpecId
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
    val activeJobRun = new JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
    val runSpecId = activeJobRun.id.toRunSpecId
    val runSpec: RunSpec = activeJobRun.toRunSpec
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
    val activeJobRun = new JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Active, clock.now(), None, Map.empty)
    val runSpecId = activeJobRun.id.toRunSpecId
    val runSpec: RunSpec = activeJobRun.toRunSpec
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

  test("RestartPolicy is handled correctly") {
    import scala.concurrent.duration._
    val f = new Fixture

    Given("a jobRunSpec with a RestartPolicy OnFailure and a 10s timeout")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(restart = RestartSpec(
        policy = RestartPolicy.OnFailure,
        activeDeadline = Some(10.seconds)
      ))
    )
    val (actor, jobRun) = f.setupActiveExecutorActor(Some(jobSpec))

    When("the task fails")
    actor ! f.statusUpdate(TaskState.Failed)

    Then("the update is propagated")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.tasks.head._2.state shouldBe TaskState.Failed
    updateMsg.startedJobRun.jobRun.tasks.head._2.completedAt shouldBe None

    And("the jobRun is updated")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunUpdated(f.persistenceActor.ref, jobRun, ()))

    And("a new task is launched")
    verify(f.launchQueue, atLeast(1)).add(any, any)

    When("there is no time left")
    f.clock += 15.seconds

    And("the second task also fails")
    actor ! f.statusUpdate(TaskState.Failed)

    verifyFailureActions(jobRun, f)
  }

  // FIXME (urgent): implement test
  ignore("A task does not become active and is killed by the overdueTasksActor and eventually a new task is launched") {
    // This should actually already be handled by the taskLauncherActor!
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

  def verifyFailureActions(jobRun: JobRun, f: Fixture): Unit = {
    import f._

    Then("the launch queue is purged")
    verify(launchQueue, timeout(1000)).purge(jobRun.id.toRunSpecId)

    And("the update is propagated")
    val parentUpdate = parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    parentUpdate.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed

    And("the jobRun is deleted")
    persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(persistenceActor.ref, jobRun, ()))

    And("the jobRun termination is propagated as well")
    val finishedMsg = parent.expectMsgType[JobRunExecutorActor.Failed]
    finishedMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val runSpecId = JobId("/test")
    val taskId = Task.Id.forRunSpec(runSpecId.toPathId)
    val defaultJobSpec = JobSpec(runSpecId, Some("test"))
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

    def setupInitialExecutorActor(): (ActorRef, JobRun) = {
      val startingJobRun = new JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Initial, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      (actorRef, startingJobRun)
    }

    def setupCreatingExecutorActor(): (ActorRef, JobRun) = {
      val startingJobRun = new JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Initial, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit))
      verify(launchQueue, timeout(1000)).add(any, any)
      (actorRef, startingJobRun)
    }

    def setupActiveExecutorActor(spec: Option[JobSpec] = None): (ActorRef, JobRun) = {
      val jobSpec = spec.getOrElse(defaultJobSpec)
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

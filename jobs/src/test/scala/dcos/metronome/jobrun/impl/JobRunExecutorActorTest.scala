package dcos.metronome
package jobrun.impl

import java.time.{ Clock, Instant, LocalDateTime, ZoneOffset }

import akka.Done
import akka.actor.{ ActorContext, ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import dcos.metronome.eventbus.TaskStateChangedEvent
import dcos.metronome.jobrun.impl.JobRunExecutorActor.ForwardStatusUpdate
import dcos.metronome.model._
import dcos.metronome.scheduler.TaskState
import dcos.metronome.utils.glue.MarathonImplicits._
import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.core.condition.Condition
import mesosphere.marathon.core.instance.{ Goal, Instance }
import mesosphere.marathon.core.instance.Instance.AgentInfo
import mesosphere.marathon.{ MarathonSchedulerDriverHolder, StoreCommandFailedException }
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedInstanceInfo
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.tracker.InstanceTracker
import mesosphere.marathon.state.Container.MesosDocker
import mesosphere.marathon.state.{ AppDefinition, RunSpec, Timestamp, UnreachableDisabled }
import mesosphere.marathon.state
import org.apache.mesos.SchedulerDriver
import org.apache.mesos
import org.apache.zookeeper.KeeperException.NodeExistsException
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest._

import scala.concurrent.{ Future, Promise }
import scala.collection.immutable.Seq
import scala.concurrent.duration._

class JobRunExecutorActorTest extends TestKit(ActorSystem("test"))
    with FunSuiteLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GivenWhenThen
    with ScalaFutures
    with Matchers
    with Eventually
    with ImplicitSender
    with Mockito {

  private implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  test("ForwardStatusUpdate STAGING with subsequent RUNNING") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, _) = f.setupStartingExecutorActor()

    When("The actor receives a status update indicating the run is active")
    actor ! f.statusUpdate(TaskState.Staging)

    Then("The updated JobRun is persisted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]

    And("The JobRun is reported active")
    val msg1 = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    msg1.startedJobRun.jobRun.status shouldBe JobRunStatus.Active

    When("A subsequent RUNNING update is processed")
    actor ! f.statusUpdate(TaskState.Running)

    Then("New task status is persisted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
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
  }

  test("ForwardStatusUpdate FAILED") {
    Given("An executor with a JobRun in state Active")
    val f = new Fixture
    val (actor, activeJobRun) = f.setupActiveExecutorActor()

    When("The actor receives a status update TASK_FAILED")
    val statusUpdate = f.statusUpdate(TaskState.Failed)
    actor ! statusUpdate

    verifyFailureActions(activeJobRun, expectedTaskCount = 1, f)
  }

  Set(TaskState.Failed, TaskState.Killed) foreach { state =>
    test(s"ForwardStatusUpdate $state in state Active") {
      Given("An executor with a JobRun in state Active")
      val f = new Fixture
      val (actor, activeJobRun) = f.setupActiveExecutorActor()

      When(s"The actor receives a status update $state")
      val statusUpdate = f.statusUpdate(state)
      actor ! statusUpdate

      verifyFailureActions(activeJobRun, expectedTaskCount = 1, f)
    }
  }

  Set(TaskState.Failed, TaskState.Killed) foreach { state =>
    test(s"ForwardStatusUpdate $state in state Starting") {
      Given("An executor with a JobRun in state Starting")
      val f = new Fixture
      val (actor, activeJobRun) = f.setupStartingExecutorActor()

      When(s"The actor receives a status update $state")
      val statusUpdate = f.statusUpdate(state)
      actor ! statusUpdate

      verifyFailureActions(activeJobRun, expectedTaskCount = 1, f)
    }
  }

  // FIXME (urgent): test this in other states as well
  test("KillCurrentJobRun") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, jobRun) = f.setupStartingExecutorActor()

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
    f.promise.future.failed.futureValue shouldEqual JobRunFailed(JobResult(jobRun.copy(
      status = JobRunStatus.Failed, completedAt = Some(f.clock.instant()))))
  }

  test("Persistence failure is propagated during starting") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, jobRun) = f.setupStartingExecutorActor()

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
  }

  test("Persistence failure is propagated during starting and deleting the jobRun also fails") {
    Given("An executor with a JobRun in state Starting")
    val f = new Fixture
    val (actor, jobRun) = f.setupStartingExecutorActor()

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
  }

  test("Init of JobRun with JobRunStatus.Success") {
    val f = new Fixture
    import f._
    val successfulJobRun = JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Success, clock.instant(), None, None, Map.empty)
    executorActor(successfulJobRun)

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
    val failedJobRun = JobRun(JobRunId(f.defaultJobSpec), f.defaultJobSpec, JobRunStatus.Failed, f.clock.instant(), Some(f.clock.instant()), None, Map.empty)

    When("an executor is initialized with the failed jobRun")
    f.executorActor(failedJobRun)

    verifyFailureActions(failedJobRun, expectedTaskCount = 0, f)
  }

  test("Init of JobRun with JobRunStatus.Active and nonexistent launchQueue") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Active")
    val activeJobRun = JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Active, clock.instant(), None, None, Map.empty)
    val runSpecId = activeJobRun.id.toRunSpecId
    f.launchQueue.get(runSpecId) returns Future.successful(None)
    instanceTracker.specInstancesSync(runSpecId) returns Seq.empty

    When("the actor is initialized")
    executorActor(activeJobRun)

    And("a task is placed onto the launch queue")
    verify(launchQueue, timeout(1000)).add(any, any)
  }

  test("Init of JobRun with JobRunStatus.Starting and EMPTY launchQueue") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Starting")
    val activeJobRun = JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Starting, clock.instant(), None, None, Map.empty)
    val runSpecId = activeJobRun.id.toRunSpecId
    val runSpec: RunSpec = activeJobRun.toRunSpec
    val queuedTaskInfo = QueuedInstanceInfo(
      runSpec = runSpec,
      inProgress = false,
      instancesLeftToLaunch = 0,
      finalInstanceCount = 0,
      backOffUntil = Timestamp(0),
      startedAt = Timestamp(clock.instant()))
    f.launchQueue.get(runSpecId) returns Future.successful(Some(queuedTaskInfo))
    instanceTracker.specInstancesSync(runSpecId) returns Seq.empty[Instance]

    When("the actor is initialized")
    executorActor(activeJobRun)

    Then("it will fetch info about queued or running tasks")
    verify(f.launchQueue, atLeastOnce).get(runSpecId)

    And("a task is placed onto the launch queue")
    verify(launchQueue, timeout(1000)).add(any, any)
  }

  test("Init of JobRun with JobRunStatus.Active and a task on the launchQueue and in the task tracker") {
    val f = new Fixture
    import f._

    Given("a JobRun with status Active")
    val activeJobRun = JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Active, clock.instant(), None, None, Map.empty)
    val runSpecId = activeJobRun.id.toRunSpecId
    val runSpec: RunSpec = activeJobRun.toRunSpec
    val queuedTaskInfo = QueuedInstanceInfo(
      runSpec = runSpec,
      inProgress = true,
      instancesLeftToLaunch = 0,
      finalInstanceCount = 1,
      backOffUntil = Timestamp(0),
      startedAt = Timestamp(clock.instant()))
    launchQueue.get(runSpecId) returns Future.successful(Some(queuedTaskInfo))
    instanceTracker.specInstancesSync(runSpecId) returns Seq(
      Instance(
        instanceId,
        AgentInfo("localhost", None, None, None, Seq.empty),
        Instance.InstanceState(Condition.Running, Timestamp.now(clock), Some(Timestamp.now(clock)), None, Goal.Running),
        Map(taskId -> mockTask(taskId, Timestamp.now(clock), mesos.Protos.TaskState.TASK_RUNNING)),
        Timestamp.now(clock), UnreachableDisabled, None))

    When("the actor is initialized")
    executorActor(activeJobRun)

    And("NO task is placed onto the launch queue")
    noMoreInteractions(launchQueue)
  }

  test("RestartPolicy is handled correctly for job that originally launched successfully") {
    import scala.concurrent.duration._
    val f = new Fixture

    Given("a jobRunSpec with a RestartPolicy OnFailure and a 10s timeout")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(restart = RestartSpec(
        policy = RestartPolicy.OnFailure,
        activeDeadline = Some(10.seconds))))
    val (actor, jobRun) = f.setupActiveExecutorActor(Some(jobSpec))

    When("the task fails")
    actor ! f.statusUpdate(TaskState.Failed)

    Then("the update is propagated")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.tasks.head._2.status shouldBe TaskState.Failed
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

    verifyFailureActions(jobRun, expectedTaskCount = 1, f)
  }

  test("RestartPolicy is handled correctly for job that failed to reached task_running") {
    import scala.concurrent.duration._
    val f = new Fixture

    Given("a jobRunSpec with a RestartPolicy OnFailure and a 10s timeout")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(restart = RestartSpec(
        policy = RestartPolicy.OnFailure,
        activeDeadline = Some(10.seconds))))
    val (actor, jobRun) = f.setupActiveExecutorActor(Some(jobSpec))
    val runSpecId = jobRun.id.toRunSpecId
    //  the task would still be in the queue
    f.launchQueue.get(runSpecId) returns Future.successful(Some(QueuedInstanceInfo(
      jobRun.toRunSpec,
      true,
      1,
      1,
      Timestamp.now(f.clock),
      Timestamp.now(f.clock))))

    When("the task fails")
    actor ! f.statusUpdate(TaskState.Failed)

    Then("the update is propagated")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
    updateMsg.startedJobRun.jobRun.tasks should have size 1
    updateMsg.startedJobRun.jobRun.tasks.head._2.status shouldBe TaskState.Failed
    updateMsg.startedJobRun.jobRun.tasks.head._2.completedAt shouldBe None

    And("the jobRun is updated")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunUpdated(f.persistenceActor.ref, jobRun, ()))

    And("a new task is launched")
    //    the add to the queue happens a second time if the restart works
    verify(f.launchQueue, atLeast(2)).add(any, any)

    When("there is no time left")
    f.clock += 15.seconds

    And("the second task also fails")
    actor ! f.statusUpdate(TaskState.Failed)

    verifyFailureActions(jobRun, expectedTaskCount = 1, f)
    //    no additional add to the queue based on no time left
    verify(f.launchQueue, atLeast(2)).add(any, any)
  }

  test("taskKillGracePeriodSeconds is passed to Marathon when launching task") {

    val f = new Fixture

    Given("a jobRunSpec with taskKillGracePeriodSeconds")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(taskKillGracePeriodSeconds = Some(10 seconds)))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[AppDefinition] = ArgumentCaptor.forClass(classOf[AppDefinition])

    And("RunSpec is submitted to LaunchQueue with correct taskKillGracePeriod")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    argument.getValue.taskKillGracePeriod shouldBe Some(10 seconds)
  }

  test("forcePullImage is passed to Marathon when launching task") {

    val f = new Fixture

    Given("a jobRunSpec with forcePullImage")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(docker = Some(DockerSpec("image", forcePullImage = true))))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[RunSpec] = ArgumentCaptor.forClass(classOf[RunSpec])

    And("RunSpec is submitted to LaunchQueue with a Docker forcePullImage")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    argument.getValue.container.get.docker.get.forcePullImage shouldBe true
  }

  test("image.forcePull for UCR is passed to Marathon when launching task") {

    val f = new Fixture

    Given("a jobRunSpec with forcePullImage")
    val image = ImageSpec(
      id = "image",
      forcePull = true)
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(ucr = Some(UcrSpec(image))))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[RunSpec] = ArgumentCaptor.forClass(classOf[RunSpec])

    And("RunSpec is submitted to LaunchQueue with a UCR forcePullImage")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    val ucrForcePullImage = argument.getValue.container.collect {
      case ucr: MesosDocker =>
        ucr.forcePullImage
    }
    ucrForcePullImage.get shouldBe true
  }

  test("file based secrets are passed") {

    val f = new Fixture

    val mountPath = "/var/secret"
    val secretName = "secretName"
    Given("a jobRunSpec with a file base secret")
    val image = ImageSpec(
      id = "image",
      forcePull = true)
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(
        ucr = Some(UcrSpec(image)),
        secrets = Map(secretName -> SecretDef("secretSource")),
        volumes = Seq(SecretVolume(mountPath, secretName))))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[RunSpec] = ArgumentCaptor.forClass(classOf[RunSpec])

    And("RunSpec is submitted to LaunchQueue with a Docker forcePullImage")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    val (marathonMountPath, marathonSecretName) = argument.getValue.container.collect {
      case ucr: MesosDocker =>
        val volumeWithMount = ucr.volumes.head
        val volumeSecretName = volumeWithMount.volume match {
          case state.SecretVolume(_, secret) => secret
          case _                             => ""
        }

        volumeWithMount.mount.mountPath -> volumeSecretName
    }.get
    marathonMountPath shouldEqual mountPath
    marathonSecretName shouldEqual secretName
  }

  test("privileged is passed to Marathon when launching task") {

    val f = new Fixture

    Given("a jobRunSpec with forcePullImage")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(docker = Some(DockerSpec("image", privileged = true))))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[RunSpec] = ArgumentCaptor.forClass(classOf[RunSpec])

    And("RunSpec is submitted to LaunchQueue with a Docker forcePullImage")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    argument.getValue.container.get.docker.get.privileged shouldBe true
  }

  test("gpus are passed to Marathon when launching task") {

    val f = new Fixture

    Given("a jobRunSpec with gpus")
    val image = ImageSpec(
      id = "image")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(ucr = Some(UcrSpec(image)), gpus = 4))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[RunSpec] = ArgumentCaptor.forClass(classOf[RunSpec])

    And("RunSpec is submitted to LaunchQueue with a correct gpus number")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    argument.getValue.resources.gpus shouldBe 4
  }

  test("docker parameters are passed to Marathon when launching task") {

    val f = new Fixture

    Given("a jobRunSpec with forcePullImage")
    val jobSpec = JobSpec(
      id = JobId("/test"),
      run = JobRunSpec(docker = Some(DockerSpec("image", parameters = Seq(new state.Parameter("key", "value"))))))
    val (_, jobRun) = f.setupInitialExecutorActor(Some(jobSpec))

    And("a new task is launched")
    val msg = f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    msg.jobRun.status shouldBe JobRunStatus.Starting
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(f.persistenceActor.ref, jobRun, Unit))
    import org.mockito.ArgumentCaptor
    val argument: ArgumentCaptor[RunSpec] = ArgumentCaptor.forClass(classOf[RunSpec])

    And("RunSpec is submitted to LaunchQueue with a Docker forcePullImage")
    verify(f.launchQueue, atLeast(1)).add(argument.capture(), any)
    argument.getValue.container.get.docker.get.parameters shouldBe jobSpec.run.docker.get.parameters
  }

  test("aborts a job run if starting deadline is reached") {
    import scala.concurrent.duration._
    val f = new Fixture

    Given("a jobRunSpec with startingDeadline")
    val jobSpec = JobSpec(
      id = JobId("/test"))
    val startingDeadline = Some(1 second)
    val (actor, jobRun) = f.setupInitialExecutorActor(Some(jobSpec), startingDeadline)
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]

    When("starting deadline is computed")
    eventually {
      actor.underlyingActor.startingDeadlineTimer.isDefined should be(true)
    }
    When("nothing happened until timeout is reached")
    f.clock += startingDeadline.get
    Then("job will become failed")
    verifyAbortedActions(jobRun, 0, f)
  }

  test("startingDeadline is cancelled when job is started") {
    val f = new Fixture

    Given("a jobRunSpec with startingDeadline")
    val (actor, _) = f.setupActiveExecutorActor()

    When("starting deadline does not exist for active job")
    eventually {
      actor.underlyingActor.startingDeadlineTimer.isDefined should be(false)
    }
  }

  test("does not attempt to start job for negative starting deadline") {
    import scala.concurrent.duration._
    val f = new Fixture

    Given("a jobRunSpec with startingDeadline")
    val jobSpec = JobSpec(
      id = JobId("/test"))
    val startingDeadline = Some(1 second)
    Given("a job run created one hour before now")
    val (actor, jobRun) = f.setupInitialExecutorActor(Some(jobSpec), startingDeadline,
      createdAt = f.clock.instant().minus(java.time.Duration.ofHours(1)))

    actor.underlyingActor.startingDeadlineTimer.isDefined should be(false)

    Then("job will fail immediately")
    eventually {
      verifyAbortedActions(jobRun, 0, f)
    }
  }

  // regression for METRONOME-100
  test("does not start a new job when jobqueue was purged after metronome restart") {
    val f = new Fixture

    Given("a jobRun already in active state")
    f.setupRunningExecutorActor()

    Then("no new tasks are launched")
    noMoreInteractions(f.launchQueue)
    Then("Nothing is persisted because the JobRunStatus is still Active")
    f.persistenceActor.expectNoMsg(500.millis)

    And("No additional message is send because the job is still active")
    f.parent.expectNoMsg(500.millis)
  }

  test("does not fail when job run already exists in ZK") {
    val f = new Fixture
    Given("a jobRun initial where node already exists")
    val (_, jobRun) = f.setupInitialExecutorActor()
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
    f.persistenceActor.reply(JobRunPersistenceActor.PersistFailed(f.persistenceActor.ref, jobRun.id, new StoreCommandFailedException("", new NodeExistsException()), ()))

    Then("run goes to starting phase instead of restarting the actor and starting from the beginning")
    verify(f.launchQueue).add(any, any)
  }

  def verifyFailureActions(jobRun: JobRun, expectedTaskCount: Int, f: Fixture): Unit = {
    import f._

    Then("The launch queue is purged")
    verify(launchQueue, timeout(1000)).purge(jobRun.id.toRunSpecId)

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, jobRun, ()))

    And("The JobRun update is reported")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.tasks should have size expectedTaskCount.toLong
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed
    updateMsg.startedJobRun.jobRun.completedAt shouldBe Some(f.clock.instant())

    And("The JobRun is reported failed")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Failed]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise is completed")
    f.promise.isCompleted
  }

  def verifyAbortedActions(jobRun: JobRun, expectedTaskCount: Int, f: Fixture): Unit = {
    import f._

    Then("The launch queue is purged")
    verify(launchQueue, timeout(1000)).purge(jobRun.id.toRunSpecId)

    And("The JobRun is deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, jobRun, ()))

    And("The JobRun update is reported")
    val updateMsg = f.parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
    updateMsg.startedJobRun.jobRun.tasks should have size expectedTaskCount.toLong
    updateMsg.startedJobRun.jobRun.status shouldBe JobRunStatus.Failed
    updateMsg.startedJobRun.jobRun.completedAt shouldBe Some(f.clock.instant())

    And("The JobRun is reported failed")
    val failMsg = f.parent.expectMsgType[JobRunExecutorActor.Aborted]
    failMsg.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise is completed")
    f.promise.isCompleted
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }

  override protected def afterEach(): Unit = {
    import JobRunExecutorActorTest._
    actor match {
      case Some(actorRef) => system.stop(actorRef)
      case _ =>
        val msg = "The test didn't set a reference to the tested actor. Either make sure to set the ref" +
          "so it can be stopped automatically, or move the test to a suite that doesn't test this actor."
        fail(msg)
    }
  }

  class Fixture {
    val runSpecId = JobId("/test")
    val taskId = Task.Id.forRunSpec(runSpecId.toPathId)
    val instanceId = Instance.Id.forRunSpec(runSpecId.toPathId)
    val defaultJobSpec = JobSpec(runSpecId, Some("test"))
    val clock = new SettableClock(Clock.fixed(LocalDateTime.parse("2016-06-01T08:50:12.000").toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
    val launchQueue: LaunchQueue = mock[LaunchQueue]
    launchQueue.purge(any).returns(Future.successful(Done))
    val instanceTracker: InstanceTracker = mock[InstanceTracker]
    launchQueue.get(any).returns(Future.successful(None))
    val driver = mock[SchedulerDriver]
    val driverHolder: MarathonSchedulerDriverHolder = {
      val holder = new MarathonSchedulerDriverHolder
      holder.driver = Some(driver)
      holder
    }

    def statusUpdate(state: TaskState) = ForwardStatusUpdate(TaskStateChangedEvent(
      taskId = taskId, taskState = state, timestamp = Clock.systemUTC().instant()))

    val persistenceActor = TestProbe()
    val persistenceActorFactory: (JobRunId, ActorContext) => ActorRef = (_, _) => persistenceActor.ref
    val promise: Promise[JobResult] = Promise[JobResult]
    val parent = TestProbe()
    implicit val scheduler = new SimulatedScheduler(clock)
    def executorActor(jobRun: JobRun, startingDeadline: Option[Duration] = None): TestActorRef[JobRunExecutorActor] = {
      import JobRunExecutorActorTest._
      val actorRef = TestActorRef[JobRunExecutorActor](JobRunExecutorActor.props(jobRun, promise, persistenceActorFactory,
        launchQueue, instanceTracker, driverHolder, clock)(scheduler), parent.ref, "JobRunExecutor")
      actor = Some(actorRef)
      actorRef
    }

    def mockTask(taskId: Task.Id, stagedAt: Timestamp, mesosState: mesos.Protos.TaskState): Task = {
      val status: Task.Status = mock[Task.Status]
      status.stagedAt returns stagedAt
      val mesosStatus: mesos.Protos.TaskStatus = mesos.Protos.TaskStatus.newBuilder()
        .setState(mesosState)
        .buildPartial()
      val task = mock[Task]
      task.taskId returns taskId
      task.status returns status
      task.status.mesosStatus returns Some(mesosStatus)
      task
    }

    /**
      *  Setup an executor actor with a JobRun in status [[JobRunStatus.Initial]]
      *
      *  @return A tuple containing the ActorRef and the JobRun
      */
    def setupInitialExecutorActor(spec: Option[JobSpec] = None, startingDeadline: Option[Duration] = None, createdAt: Instant = clock.instant()): (TestActorRef[JobRunExecutorActor], JobRun) = {
      val jobSpec = spec.getOrElse(defaultJobSpec)
      val startingJobRun = JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Initial, createdAt, None, startingDeadline, Map.empty)
      val actorRef = executorActor(startingJobRun, startingDeadline)
      (actorRef, startingJobRun)
    }

    /**
      * Setup an executor actor with a JobRun in status [[JobRunStatus.Starting]]
      *
      * @return A tuple containing the ActorRef and the JobRun
      */
    def setupStartingExecutorActor(): (ActorRef, JobRun) = {
      val startingJobRun = JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Initial, clock.instant(), None, None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      val msg = persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      msg.jobRun.status shouldBe JobRunStatus.Starting
      persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit))
      verify(launchQueue, timeout(1000)).add(any, any)
      (actorRef, startingJobRun)
    }

    def setupRunningExecutorActor(): (ActorRef, JobRun) = {
      val activeJob = JobRun(JobRunId(defaultJobSpec), defaultJobSpec, JobRunStatus.Active, clock.instant(), None, None,
        Map(Task.Id("app_682ebe64-0771-11e4-b05d-e0f84720c54e") -> JobRunTask(Task.Id("app_682ebe64-0771-11e4-b05d-e0f84720c54e"), null, None, TaskState.Running)))
      val actorRef: ActorRef = executorActor(activeJob)
      val runSpecId = activeJob.id.toRunSpecId
      instanceTracker.specInstancesSync(runSpecId) returns Seq(
        Instance(
          instanceId,
          AgentInfo("localhost", None, None, None, Seq.empty),
          Instance.InstanceState(Condition.Running, Timestamp.now(clock), Some(Timestamp.now(clock)), None, Goal.Running),
          Map(taskId -> mockTask(taskId, Timestamp.now(clock), mesos.Protos.TaskState.TASK_RUNNING)),
          Timestamp.now(clock), UnreachableDisabled, None))
      (actorRef, activeJob)
    }

    /**
      * Setup an executor actor with a JobRun in status [[JobRunStatus.Active]]
      *
      * @param spec (Optional) the JobSpec to use if the default shouldn't be used
      * @return A tuple containing the ActorRef and the JobRun
      */
    def setupActiveExecutorActor(spec: Option[JobSpec] = None, startingDeadline: Option[Duration] = None): (TestActorRef[JobRunExecutorActor], JobRun) = {
      val jobSpec = spec.getOrElse(defaultJobSpec)
      val startingJobRun = JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Initial, clock.instant(), None, None, Map.empty)
      val actorRef: TestActorRef[JobRunExecutorActor] = executorActor(startingJobRun, startingDeadline)
      persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      persistenceActor.reply(JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit))
      verify(launchQueue, timeout(1000)).add(any, any)
      actorRef ! ForwardStatusUpdate(TaskStateChangedEvent(
        taskId = taskId,
        taskState = TaskState.Running,
        timestamp = clock.instant()))
      val updateMsg = persistenceActor.expectMsgType[JobRunPersistenceActor.Update]
      persistenceActor.reply(JobRunPersistenceActor.JobRunUpdated(persistenceActor.ref, updateMsg.change(startingJobRun), ()))
      val parentUpdate = parent.expectMsgType[JobRunExecutorActor.JobRunUpdate]
      parentUpdate.startedJobRun.jobRun.status shouldBe JobRunStatus.Active
      (actorRef, startingJobRun)
    }
  }
}

object JobRunExecutorActorTest {
  var actor: Option[ActorRef] = None
}

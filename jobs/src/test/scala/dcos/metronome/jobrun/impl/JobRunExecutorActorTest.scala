package dcos.metronome.jobrun.impl

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import dcos.metronome.jobrun.impl.JobRunExecutorActor.ForwardStatusUpdate
import dcos.metronome.model.{JobResult, JobRun, JobRunId, JobRunStatus, JobSpec}
import dcos.metronome.utils.glue.MarathonImplicits.RunSpecId
import dcos.metronome.utils.test.Mockito
import dcos.metronome.utils.time.FixedClock
import mesosphere.marathon.MarathonSchedulerDriverHolder
import mesosphere.marathon.core.launchqueue.LaunchQueue
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.event.MesosStatusUpdateEvent
import mesosphere.marathon.state.PathId
import org.apache.mesos
import org.joda.time.DateTime
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, GivenWhenThen, Matchers}

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
    actor ! f.statusUpdate(mesos.Protos.TaskState.TASK_FINISHED)

    Then("The launch queue is purged")
    verify(f.launchQueue).purge(RunSpecId(activeJobRun.id))

    And("The JobRun deleted")
    f.persistenceActor.expectMsgType[JobRunPersistenceActor.Delete]
    f.persistenceActor.reply(JobRunPersistenceActor.JobRunDeleted(f.persistenceActor.ref, activeJobRun, ()))

    And("The JobRun is reported successful")
    val msg1 = f.parent.expectMsgType[JobRunExecutorActor.Finished]
    msg1.jobResult.jobRun.status shouldBe JobRunStatus.Success

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
    val msg1 = f.parent.expectMsgType[JobRunExecutorActor.Failed]
    msg1.jobResult.jobRun.status shouldBe JobRunStatus.Failed

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
    val msg1 = f.parent.expectMsgType[JobRunExecutorActor.Failed]
    msg1.jobResult.jobRun.status shouldBe JobRunStatus.Failed

    And("the promise is completed")
    f.promise.isCompleted

    system.stop(actor)

  }
  /* TODO:
    - PersistFailed
    */
  override protected def afterAll(): Unit = {
    shutdown()
  }

  class Fixture {
    val id = PathId("/test")
    val jobSpec = JobSpec(id, Some("test"))
    val clock = new FixedClock(DateTime.parse("2016-06-01T08:50:12.000Z"))
    val launchQueue: LaunchQueue = mock[LaunchQueue]
    val driverHolder: MarathonSchedulerDriverHolder = mock[MarathonSchedulerDriverHolder]

    def statusUpdate(state: mesos.Protos.TaskState) = ForwardStatusUpdate(MesosStatusUpdateEvent(
      "slaveId", Task.Id("taskId"), state.toString, "message", jobSpec.id, "host",
      ipAddresses = None, ports = Seq.empty[Int], "version", "eventType", DateTime.now().toString))

    val persistenceActor = TestProbe()
    val persistenceActorFactory: (JobRunId, ActorContext) => ActorRef = (_, context) => persistenceActor.ref
    val promise: Promise[JobResult] = Promise[JobResult]
    val parent = TestProbe()
    def executorActor(jobRun: JobRun) = {
      TestActorRef(JobRunExecutorActor.props(jobRun, promise, persistenceActorFactory, launchQueue, driverHolder, clock), parent.ref, "JobRunExecutor")
    }

    def initializeStartedExecutorActor() = {
      val startingJobRun = new JobRun(JobRunId(jobSpec), jobSpec, JobRunStatus.Starting, clock.now(), None, Map.empty)
      val actorRef: ActorRef = executorActor(startingJobRun)
      persistenceActor.expectMsgType[JobRunPersistenceActor.Create]
      actorRef ! JobRunPersistenceActor.JobRunCreated(persistenceActor.ref, startingJobRun, Unit)
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

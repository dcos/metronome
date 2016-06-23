package dcos.metronome.scheduler

import dcos.metronome.utils.test.Mockito
import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }
import org.apache.mesos

class TaskStateTest extends FunSuite with Mockito with Matchers with GivenWhenThen {

  test("Mesos TaskState -> TaskState") {
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_ERROR)) shouldBe TaskState.Failed
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_FAILED)) shouldBe TaskState.Failed
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_FINISHED)) shouldBe TaskState.Finished
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_KILLED)) shouldBe TaskState.Killed
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_KILLING)) shouldBe TaskState.Running
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_LOST)) shouldBe TaskState.Failed
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_RUNNING)) shouldBe TaskState.Running
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_STAGING)) shouldBe TaskState.Staging
    TaskState(taskStatus(mesos.Protos.TaskState.TASK_STARTING)) shouldBe TaskState.Starting
  }

  def taskStatus(state: mesos.Protos.TaskState): mesos.Protos.TaskStatus = {
    mesos.Protos.TaskStatus.newBuilder()
      .setState(state)
      .buildPartial()
  }
}

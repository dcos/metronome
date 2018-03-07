package dcos.metronome
package scheduler

import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.core.condition.Condition
import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }
import org.apache.mesos

class TaskStateTest extends FunSuite with Mockito with Matchers with GivenWhenThen {

  test("Mesos TaskState -> TaskState") {
    TaskState(Condition.Error) shouldBe Some(TaskState.Failed)
    TaskState(Condition.Failed) shouldBe Some(TaskState.Failed)
    TaskState(Condition.Finished) shouldBe Some(TaskState.Finished)
    TaskState(Condition.Killed) shouldBe Some(TaskState.Killed)
    TaskState(Condition.Killing) shouldBe Some(TaskState.Running)
    TaskState(Condition.Unreachable) shouldBe Some(TaskState.Failed)
    TaskState(Condition.Running) shouldBe Some(TaskState.Running)
    TaskState(Condition.Staging) shouldBe Some(TaskState.Staging)
    TaskState(Condition.Starting) shouldBe Some(TaskState.Starting)
  }

  def taskStatus(state: mesos.Protos.TaskState): mesos.Protos.TaskStatus = {
    mesos.Protos.TaskStatus.newBuilder()
      .setState(state)
      .buildPartial()
  }
}

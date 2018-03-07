package dcos.metronome
package scheduler

import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.core.condition.Condition
import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }
import org.apache.mesos

class TaskStateTest extends FunSuite with Mockito with Matchers with GivenWhenThen {

  test("Mesos TaskState -> TaskState") {
    TaskState(Condition.Error) shouldBe TaskState.Failed
    TaskState(Condition.Failed) shouldBe TaskState.Failed
    TaskState(Condition.Finished) shouldBe TaskState.Finished
    TaskState(Condition.Killed) shouldBe TaskState.Killed
    TaskState(Condition.Killing) shouldBe TaskState.Running
    TaskState(Condition.Unreachable) shouldBe TaskState.Failed
    TaskState(Condition.Running) shouldBe TaskState.Running
    TaskState(Condition.Staging) shouldBe TaskState.Staging
    TaskState(Condition.Starting) shouldBe TaskState.Starting
  }

  def taskStatus(state: mesos.Protos.TaskState): mesos.Protos.TaskStatus = {
    mesos.Protos.TaskStatus.newBuilder()
      .setState(state)
      .buildPartial()
  }
}

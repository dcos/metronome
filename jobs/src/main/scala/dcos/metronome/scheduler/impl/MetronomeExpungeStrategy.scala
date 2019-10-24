package dcos.metronome.scheduler.impl

import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.core.instance.update.InstanceExpungeStrategy
import org.apache.mesos.{ Protos => MesosProtos }

case object MetronomeExpungeStrategy extends InstanceExpungeStrategy {
  def shouldBeExpunged(instance: Instance): Boolean =
    instance.tasksMap.values.forall(t => t.isTerminal) && instance.reservation.isEmpty

  def shouldAbandonReservation(instance: Instance): Boolean = {

    def allAreTerminal = instance.tasksMap.values.iterator.forall { task =>
      task.status.condition.isTerminal
    }

    def anyAreGoneByOperator = instance.tasksMap.values.iterator
      .flatMap(_.status.mesosStatus)
      .exists { status =>
        status.getState == MesosProtos.TaskState.TASK_GONE_BY_OPERATOR
      }

    instance.reservation.nonEmpty && anyAreGoneByOperator && allAreTerminal
  }
}
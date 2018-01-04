package dcos.metronome
package jobrun.impl

import dcos.metronome.model._
import mesosphere.marathon.Protos.Constraint
import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedTaskInfo
import mesosphere.marathon.state.{ Container, RunSpec }
import org.slf4j.LoggerFactory

import scala.collection.immutable.Seq
import scala.util.Try

object QueuedJobRunConverter {

  private[impl] val log = LoggerFactory.getLogger(getClass)

  implicit class MarathonProtoToConstraintSpec(val constraints: Set[Constraint]) extends AnyVal {

    def toModel: Seq[dcos.metronome.model.ConstraintSpec] = constraints.map { constraint =>
      val value = if (constraint.hasValue) Some(constraint.getValue) else None
      val operator = constraint.getOperator
      if (!Operator.names.contains(operator.toString)) {
        log.error(s"Constraint operator not an option: $operator")
      }
      ConstraintSpec(
        attribute = constraint.getField,
        operator = Operator.names(operator.toString),
        value = value
      )
    }.toList
  }

  implicit class MarathonContainerToDockerSpec(val container: Option[Container]) extends AnyVal {

    def toModel: Option[DockerSpec] = {
      if (container.isEmpty || container.get.docker().isEmpty)
        return None
      val docker = container.get.docker().get
      Some(DockerSpec(docker.image, docker.forcePullImage))
    }
  }

  implicit class RunSpecToJobRunSpec(val run: RunSpec) extends AnyVal {

    def toModel: JobRunSpec = {
      val placement: PlacementSpec = convertPlacement
      JobRunSpec(
        run.cpus,
        run.mem,
        run.disk,
        run.cmd,
        run.args,
        run.user,
        placement = placement,
        maxLaunchDelay = run.maxLaunchDelay,
        taskKillGracePeriodSeconds = run.taskKillGracePeriod,
        docker = run.container.toModel
      )
    }

    // TODO: remove once placement is fixed.
    private def convertPlacement = {
      try {
        PlacementSpec(run.constraints.toModel)
      } catch {
        case e: Exception => PlacementSpec()
      }
    }
  }

  implicit class QueuedTaskInfoToQueuedJobRunInfo(val taskInfo: QueuedTaskInfo) extends AnyVal {

    def toModel: QueuedJobRunInfo = {
      QueuedJobRunInfo(
        id = taskInfo.runSpec.id,
        tasksLost = taskInfo.tasksLost,
        backOffUntil = taskInfo.backOffUntil,
        run = taskInfo.runSpec.toModel,
        acceptedResourceRoles = Some(taskInfo.runSpec.acceptedResourceRoles.getOrElse(Set("*")))
      )
    }
  }
}

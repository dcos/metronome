package dcos.metronome
package jobrun.impl

import dcos.metronome.model._
import mesosphere.marathon.Protos.Constraint
import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedInstanceInfo
import mesosphere.marathon.state.{ AppDefinition, Container }
import org.slf4j.LoggerFactory

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
        value = value)
    }.toList
  }

  implicit class MarathonContainerToDockerSpec(val container: Option[Container]) extends AnyVal {

    def toModel: Option[DockerSpec] = container.flatMap(c => c.docker).map(d => DockerSpec(d.image, d.forcePullImage))
  }

  implicit class RunSpecToJobRunSpec(val run: AppDefinition) extends AnyVal {

    def toModel: JobRunSpec = {
      val placement: PlacementSpec = convertPlacement
      JobRunSpec(
        run.resources.cpus,
        run.resources.mem,
        run.resources.disk,
        run.cmd,
        Some(run.args),
        run.user,
        placement = placement,
        maxLaunchDelay = run.backoffStrategy.maxLaunchDelay,
        taskKillGracePeriodSeconds = run.taskKillGracePeriod,
        docker = run.container.toModel)
    }

    // TODO: remove once placement is fixed.
    private def convertPlacement = {
      try {
        PlacementSpec(run.constraints.toModel)
      } catch {
        case _: Exception => PlacementSpec()
      }
    }
  }

  implicit class QueuedTaskInfoToQueuedJobRunInfo(val instanceInfo: QueuedInstanceInfo) extends AnyVal {

    def toModel: QueuedJobRunInfo = {
      val jobRunSpec = instanceInfo.runSpec match {
        case app: AppDefinition => app.toModel
        case runSpec =>
          throw new IllegalArgumentException(s"Unexpected runSpec type - jobs are translated to Apps on Marathon level, got $runSpec")
      }
      QueuedJobRunInfo(
        id = instanceInfo.runSpec.id,
        backOffUntil = instanceInfo.backOffUntil,
        run = jobRunSpec,
        acceptedResourceRoles = instanceInfo.runSpec.acceptedResourceRoles)
    }
  }
}

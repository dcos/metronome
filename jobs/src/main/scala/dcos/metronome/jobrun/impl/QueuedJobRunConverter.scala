package dcos.metronome
package jobrun.impl

import dcos.metronome.model._
import mesosphere.marathon.Protos.Constraint
import mesosphere.marathon.state.Container.{ Docker, MesosDocker }
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

    def toDockerModel: Option[DockerSpec] = container.map{ case d: Docker => DockerSpec(d.image, d.forcePullImage) }

    def toUcrModel: Option[UcrSpec] = container.collect {
      case ucr: MesosDocker =>
        val image = ImageSpec(id = ucr.image, forcePull = ucr.forcePullImage)
        UcrSpec(image, privileged = false) // TODO: Add privileged once marathon will support it
    }
  }

  implicit class RunSpecToJobRunSpec(val run: AppDefinition) extends AnyVal {

    def toModel: JobRunSpec = {
      val placement: PlacementSpec = convertPlacement
      JobRunSpec(
        run.resources.cpus,
        run.resources.mem,
        run.resources.disk,
        run.resources.gpus,
        run.cmd,
        Some(run.args),
        run.user,
        placement = placement,
        maxLaunchDelay = run.backoffStrategy.maxLaunchDelay,
        taskKillGracePeriodSeconds = run.taskKillGracePeriod,
        docker = run.container.toDockerModel,
        ucr = run.container.toUcrModel)
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
}

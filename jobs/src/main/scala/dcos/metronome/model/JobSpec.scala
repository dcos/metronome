package dcos.metronome.model

import com.wix.accord.dsl._
import com.wix.accord.Validator
import mesosphere.marathon.api.v2.Validation._
import mesosphere.marathon.state.PathId

import scala.collection.immutable._

case class JobSpec(
    id:          PathId,
    description: Option[String]      = JobSpec.DefaultDescription,
    labels:      Map[String, String] = JobSpec.DefaultLabels,
    schedules:   Seq[ScheduleSpec]   = JobSpec.DefaultSchedule,
    run:         RunSpec             = JobSpec.DefaultRunSpec
) {
  def schedule(id: String): Option[ScheduleSpec] = schedules.find(_.id == id)
}

object JobSpec {
  val DefaultDescription = None
  val DefaultLabels = Map.empty[String, String]
  val DefaultSchedule = Seq.empty[ScheduleSpec]
  val DefaultRunSpec = RunSpec()

  implicit lazy val validJobSpec: Validator[JobSpec] = validator[JobSpec] { jobSpec =>
    jobSpec.id is valid and PathId.absolutePathValidator
    jobSpec.schedules is every(valid)
    jobSpec.schedules has size <= 1 // FIXME: we will support only one schedule in v1
  }
}


package dcos.metronome.model

import com.wix.accord.dsl._
import com.wix.accord.Validator
import mesosphere.marathon.api.v2.Validation._
import mesosphere.marathon.state.PathId

case class JobSpec(
  id:          PathId,
  description: String,
  labels:      Map[String, String]  = JobSpec.DefaultLabels,
  schedule:    Option[ScheduleSpec] = JobSpec.DefaultSchedule,
  run:         RunSpec              = JobSpec.DefaultRunSpec
)

object JobSpec {
  val DefaultLabels = Map.empty[String, String]
  val DefaultSchedule = None
  val DefaultRunSpec = RunSpec()

  implicit lazy val validJobSpec: Validator[JobSpec] = validator[JobSpec] { jobSpec =>
    jobSpec.id is valid and PathId.absolutePathValidator
    jobSpec.schedule is optional(ScheduleSpec.validScheduleSpec)
  }
}


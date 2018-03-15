package dcos.metronome
package model

import com.wix.accord.Validator
import com.wix.accord.dsl._
import dcos.metronome.model.JobRunSpec._
import mesosphere.marathon.api.v2.Validation._
import mesosphere.marathon.plugin.{ AppVolumeSpec, ApplicationSpec, EnvVarValue, NetworkSpec, Secret }

import scala.collection.immutable._

case class JobSpec(
  id:          JobId,
  description: Option[String]      = JobSpec.DefaultDescription,
  labels:      Map[String, String] = JobSpec.DefaultLabels,
  schedules:   Seq[ScheduleSpec]   = JobSpec.DefaultSchedule,
  run:         JobRunSpec          = JobSpec.DefaultRunSpec) extends ApplicationSpec {
  def schedule(id: String): Option[ScheduleSpec] = schedules.find(_.id == id)

  override val user: Option[String] = run.user
  override val acceptedResourceRoles: Set[String] = Set.empty
  override val secrets: Map[String, Secret] = Map.empty
  override val env: Map[String, EnvVarValue] = mesosphere.marathon.state.EnvVarValue(run.env)
  override val volumes: Seq[AppVolumeSpec] = Seq.empty
  override val networks: Seq[NetworkSpec] = Seq.empty
}

object JobSpec {
  val DefaultDescription = None
  val DefaultLabels = Map.empty[String, String]
  val DefaultSchedule = Seq.empty[ScheduleSpec]
  val DefaultRunSpec = JobRunSpec()

  implicit lazy val validJobSpec: Validator[JobSpec] = validator[JobSpec] { jobSpec =>
    jobSpec.id is valid
    jobSpec.schedules is every(valid)
    jobSpec.run is valid
    jobSpec.schedules has size <= 1 // FIXME: we will support only one schedule in v1
  }
}

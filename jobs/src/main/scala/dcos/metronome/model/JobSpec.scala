package dcos.metronome
package model

import com.wix.accord.{NullSafeValidator, Validator}
import com.wix.accord.dsl._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.JobRunSpec._
import dcos.metronome.utils.glue.MarathonConversions
import mesosphere.marathon.api.v2.Validation._
import mesosphere.marathon
import mesosphere.marathon.plugin.{ApplicationSpec, NetworkSpec, Secret, VolumeMountSpec, VolumeSpec}

import scala.concurrent.Await

case class JobSpec(
    id: JobId,
    description: Option[String] = JobSpec.DefaultDescription,
    dependencies: Option[Seq[JobId]] = JobSpec.DefaultDependencies,
    labels: Map[String, String] = JobSpec.DefaultLabels,
    schedules: Seq[ScheduleSpec] = JobSpec.DefaultSchedule,
    run: JobRunSpec = JobSpec.DefaultRunSpec
) extends ApplicationSpec {
  def schedule(id: String): Option[ScheduleSpec] = schedules.find(_.id == id)

  override val user: Option[String] = run.user
  override val acceptedResourceRoles: Set[String] = Set.empty
  override val secrets: Map[String, Secret] = MarathonConversions.secretsToMarathon(run.secrets)
  override val env: Map[String, marathon.state.EnvVarValue] = MarathonConversions.envVarToMarathon(run.env)
  override val volumes: Seq[VolumeSpec] = Seq.empty
  override val networks: Seq[NetworkSpec] = run.networks.map(MarathonConversions.networkToMarathon)
  override val volumeMounts: Seq[VolumeMountSpec] = Seq.empty
}

object JobSpec {
  val DefaultDescription = None
  val DefaultDependencies = Option.empty[Seq[JobId]]
  val DefaultLabels = Map.empty[String, String]
  val DefaultSchedule = Seq.empty[ScheduleSpec]
  val DefaultRunSpec = JobRunSpec()

  import com.wix.accord.ViolationBuilder._

  private def unique[T]: Validator[Seq[T]] = new NullSafeValidator[Seq[T]](
    test = col => col.size == col.toSet.size,
    failure = _ -> s"has double entries"
  )

  def acyclic[JobId]: Validator[Seq[JobId]] = new NullSafeValidator[Seq[JobId]](
    test = _ => false, // TODO: check if the dependencies are cyclic.
    failure = _ -> "is not acyclic"
  )

  def validJobSpec(jobSpecService: JobSpecService): Validator[JobSpec] = validator[JobSpec] { jobSpec =>

    def exist: Validator[Seq[JobId]] = new NullSafeValidator[Seq[JobId]](
      test = ids => ids.exists(id => Await.result(jobSpecService.getJobSpec(id), ???).isEmpty),
      failure = _ -> "has dependencies that do not exist" // TODO: specify which dependencies.
    )

    jobSpec.id is valid
    jobSpec.schedules is every(valid)
    jobSpec.run is valid
    jobSpec.schedules has size <= 1 // FIXME: we will support only one schedule in v1
    jobSpec.dependencies.each is unique[JobId]
    jobSpec.dependencies.each should exist
    jobSpec.dependencies.each is acyclic
  }
}

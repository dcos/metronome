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
    dependencies: Seq[JobId] = JobSpec.DefaultDependencies,
    labels: Map[String, String] = JobSpec.DefaultLabels,
    schedules: Seq[ScheduleSpec] = JobSpec.DefaultSchedules,
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
  val DefaultDependencies = Seq.empty[JobId]
  val DefaultLabels = Map.empty[String, String]
  val DefaultSchedules = Seq.empty[ScheduleSpec]
  val DefaultRunSpec = JobRunSpec()

  import com.wix.accord.ViolationBuilder._

  private def unique[T]: Validator[Seq[T]] =
    new NullSafeValidator[Seq[T]](
      test = col => col.size == col.toSet.size,
      failure = _ -> s"has double entries"
    )

  implicit val validJobSpec: Validator[JobSpec] =
    validator[JobSpec] { jobSpec =>
      jobSpec.id is valid
      jobSpec.schedules is every(valid)
      jobSpec.run is valid
      jobSpec.schedules has size <= 1 // FIXME: we will support only one schedule in v1
      jobSpec.dependencies is unique[JobId]
    }

  final case class ValidationError(errorMsg: String) extends Exception(errorMsg, null)

  def validateDependencies(jobSpec: JobSpec, allSpecs: Seq[JobSpec]): Unit = {
    val index = allSpecs.map(s => s.id -> s).toMap

    // Validate that all dependencies are known.
    val directDependencies = jobSpec.dependencies.filter(index.contains)
    val unknownDependencies = jobSpec.dependencies.toSet -- directDependencies
    if (unknownDependencies.nonEmpty) {
      throw ValidationError(
        s"Dependencies contain unknown jobs. unknown=[${unknownDependencies.mkString(", ")}]"
      )
    }

    // Validate that graph is acyclic

    /* Assumption: The current state allSpecs has now cycles.
     Thus:
        - if job spec A is new we cannot create a DAG with cycles since A has no incoming vertices.
        - if job spec A is updated we check if we can find a path from one of A's dependencies to A.
     */
    if (index.contains(jobSpec.id)) {
      if (jobSpec.dependencies.exists(parent => findPath(index, parent, jobSpec))) {
        throw ValidationError(
          s"Dependencies have a cycle."
        )
      }
    }
  }

  /**
    * Finds whether there is a path from `start` to `end`.
    *
    * This algorithm is a very crude DFS. It does visit nodes multiple times.
    *
    * @param specs an index of job specifications.
    * @param start the job spec id where we start.
    * @param end the job spec we want to find the path to.
    * @return true if there is a path, false if not.
    */
  def findPath(specs: Map[JobId, JobSpec], start: JobId, end: JobSpec): Boolean = {
    if (start == end.id) {
      true
    } else {
      val parentSpec = specs(start)
      parentSpec.dependencies.exists(findPath(specs, _, end))
    }
  }
}

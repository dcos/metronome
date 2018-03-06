package dcos.metronome
package model

import mesosphere.marathon.plugin.{ AppVolumeSpec, ApplicationSpec, EnvVarValue, NetworkSpec, PathId, Secret }
import mesosphere.marathon.state.Timestamp

import scala.collection.immutable.Map

case class QueuedJobRunInfo(
  // is the full id used by marathon which includes the jobid/runid example: /startdeadline/201801221711083L7i6
  id:                    PathId,
  backOffUntil:          Timestamp,
  run:                   JobRunSpec  = JobRunSpec(),
  acceptedResourceRoles: Set[String] = Set.empty) extends ApplicationSpec {
  def jobId: String = id.path.head
  lazy val runId: String = id.path.last
  override val user: Option[String] = run.user
  override val secrets: Map[String, Secret] = Map.empty
  override val env: Map[String, EnvVarValue] = mesosphere.marathon.state.EnvVarValue(run.env)
  override val labels = Map.empty[String, String]
  override val volumes: Seq[AppVolumeSpec] = Seq.empty
  override val networks: Seq[NetworkSpec] = Seq.empty
}

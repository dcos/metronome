package dcos.metronome
package model

import dcos.metronome.utils.glue.MarathonConversions
import mesosphere.marathon.plugin.{ ApplicationSpec, NetworkSpec, PathId, Secret, VolumeMountSpec, VolumeSpec }
import mesosphere.marathon.plugin
import mesosphere.marathon.state.Timestamp

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
  override val env: Map[String, plugin.EnvVarValue] = MarathonConversions.envVarToMarathon(run.env)
  override val labels = Map.empty[String, String]
  override val volumes: Seq[VolumeSpec] = Seq.empty
  override val networks: Seq[NetworkSpec] = Seq.empty
  override val volumeMounts: Seq[VolumeMountSpec] = Seq.empty
}

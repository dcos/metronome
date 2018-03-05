package dcos.metronome
package model

import mesosphere.marathon.plugin.{ EnvVarValue, PathId, RunSpec, Secret }
import mesosphere.marathon.state.Timestamp

import scala.collection.immutable.Map

case class QueuedJobRunInfo(
  // is the full id used by marathon which includes the jobid/runid example: /startdeadline/201801221711083L7i6
  id:                    PathId,
  backOffUntil:          Timestamp,
  run:                   JobRunSpec          = JobRunSpec(),
  acceptedResourceRoles: Option[Set[String]] = None) extends RunSpec {
  def jobId: String = id.path.head
  lazy val runId: String = id.path.last
  override def user: Option[String] = run.user
  override def secrets: Map[String, Secret] = Map.empty
  override def env: Map[String, EnvVarValue] = mesosphere.marathon.state.EnvVarValue(run.env)
  override def labels = Map.empty[String, String]
}

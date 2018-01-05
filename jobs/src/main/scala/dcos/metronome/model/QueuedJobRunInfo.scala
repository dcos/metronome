package dcos.metronome
package model

import mesosphere.marathon.plugin.{ EnvVarValue, PathId, RunSpec, Secret }
import mesosphere.marathon.state.Timestamp

import scala.collection.immutable.Map

case class QueuedJobRunInfo(
  id:                    PathId,
  tasksLost:             Int,
  backOffUntil:          Timestamp,
  run:                   JobRunSpec          = JobRunSpec(),
  acceptedResourceRoles: Option[Set[String]] = None) extends RunSpec {
  def jobid: String = id.path.head
  override def user: Option[String] = run.user
  override def secrets: Map[String, Secret] = Map.empty
  override def env: Map[String, EnvVarValue] = mesosphere.marathon.state.EnvVarValue(run.env)
  override def labels = Map.empty[String, String]
}

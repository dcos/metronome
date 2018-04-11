package dcos.metronome
package model

import dcos.metronome.utils.glue.MarathonConversions
import mesosphere.marathon.plugin.{ PathId, Secret, RunSpec }
import mesosphere.marathon.plugin
import mesosphere.marathon.state.Timestamp

import scala.collection.immutable.Map

case class QueuedJobRunInfo(
  // is the full id used by marathon which includes the jobid/runid example: /startdeadline/201801221711083L7i6
  id:                    PathId,
  tasksLost:             Int,
  backOffUntil:          Timestamp,
  run:                   JobRunSpec          = JobRunSpec(),
  acceptedResourceRoles: Option[Set[String]] = None) extends RunSpec {
  def jobId: String = id.path.head
  lazy val runId: String = id.path.last
  override def user: Option[String] = run.user
  override def secrets: Map[String, Secret] = Map.empty
  override val env: Map[String, plugin.EnvVarValue] = MarathonConversions.envVarToMarathon(run.env)
  override val labels = Map.empty[String, String]
}

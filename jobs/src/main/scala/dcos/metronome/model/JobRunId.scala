package dcos.metronome.model

import java.util.UUID

import mesosphere.marathon.state.PathId

case class JobRunId(value: String) {
  lazy val jobSpecId: PathId = PathId.fromSafePath(value).parent
  override def toString: String = value
}

object JobRunId {
  def apply(spec: JobSpec): JobRunId = {
    val runId = spec.id / UUID.randomUUID.toString
    JobRunId(runId.safePath)
  }
}


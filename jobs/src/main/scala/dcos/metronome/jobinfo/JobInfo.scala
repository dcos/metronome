package dcos.metronome.jobinfo

import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.{ JobHistory, JobRunSpec, ScheduleSpec, JobSpec }
import mesosphere.marathon.state.PathId

/**
  * This class represents a JobSpec with optional enriched data.
  */
case class JobInfo(
  id:          PathId,
  description: Option[String],
  labels:      Map[String, String],
  run:         JobRunSpec,
  schedules:   Option[Seq[ScheduleSpec]],
  activeRuns:  Option[Iterable[StartedJobRun]],
  history:     Option[JobHistory]
)

object JobInfo {
  sealed trait Embed
  object Embed {
    val names: Map[String, Embed] = Map(
      "activeRuns" -> ActiveRuns,
      "schedules" -> Schedules,
      "history" -> History
    )
    case object Schedules extends Embed
    case object ActiveRuns extends Embed
    case object History extends Embed
  }

  def apply(
    spec:      JobSpec,
    schedules: Option[Seq[ScheduleSpec]],
    runs:      Option[Iterable[StartedJobRun]],
    status:    Option[JobHistory]
  ): JobInfo = {
    JobInfo(spec.id, spec.description, spec.labels, spec.run, schedules, runs, status)
  }
}


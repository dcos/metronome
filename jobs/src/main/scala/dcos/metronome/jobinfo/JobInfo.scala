package dcos.metronome
package jobinfo

import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model._

/**
  * This class represents a JobSpec with optional enriched data.
  */
case class JobInfo(
  id:             JobId,
  description:    Option[String],
  labels:         Map[String, String],
  run:            JobRunSpec,
  schedules:      Option[Seq[ScheduleSpec]],
  activeRuns:     Option[Iterable[StartedJobRun]],
  history:        Option[JobHistory],
  historySummary: Option[JobHistorySummary])

object JobInfo {
  sealed trait Embed
  object Embed {
    val names: Map[String, Embed] = Map(
      "activeRuns" -> ActiveRuns,
      "schedules" -> Schedules,
      "history" -> History,
      "historySummary" -> HistorySummary)
    case object Schedules extends Embed
    case object ActiveRuns extends Embed
    case object History extends Embed
    case object HistorySummary extends Embed
  }

  def apply(
    spec:      JobSpec,
    schedules: Option[Seq[ScheduleSpec]],
    runs:      Option[Iterable[StartedJobRun]],
    history:   Option[JobHistory],
    summary:   Option[JobHistorySummary]): JobInfo = {
    JobInfo(spec.id, spec.description, spec.labels, spec.run, schedules, runs, history, summary)
  }
}

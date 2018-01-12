package dcos.metronome
package model

import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC

trait Event {
  val eventType: String
  val timestamp: DateTime
}

object Event {

  trait JobSpecEvent extends Event
  case class JobSpecCreated(
    job:       JobSpec,
    eventType: String   = "job_created",
    timestamp: DateTime = DateTime.now(UTC)) extends JobSpecEvent

  case class JobSpecUpdated(
    job:       JobSpec,
    eventType: String   = "job_updated",
    timestamp: DateTime = DateTime.now(UTC)) extends JobSpecEvent

  case class JobSpecDeleted(
    job:       JobSpec,
    eventType: String   = "job_deleted",
    timestamp: DateTime = DateTime.now(UTC)) extends JobSpecEvent

  trait JobRunEvent extends Event
  case class JobRunStarted(
    jobRun:    JobRun,
    eventType: String   = "job_run_started",
    timestamp: DateTime = DateTime.now(UTC)) extends JobRunEvent

  case class JobRunUpdate(
    jobRun:    JobRun,
    eventType: String   = "job_run_updated",
    timestamp: DateTime = DateTime.now(UTC)) extends JobRunEvent

  case class JobRunFinished(
    jobRun:    JobRun,
    eventType: String   = "job_run_finished",
    timestamp: DateTime = DateTime.now(UTC)) extends JobRunEvent

  case class JobRunFailed(
    jobRun:    JobRun,
    eventType: String   = "job_run_failed",
    timestamp: DateTime = DateTime.now(UTC)) extends JobRunEvent

  trait ReconciliationEvent extends Event
  case class ReconciliationFinished(
    eventType: String   = "job_run_failed",
    timestamp: DateTime = DateTime.now(UTC)) extends ReconciliationEvent

}

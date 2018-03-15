package dcos.metronome
package model

import java.time.{ Clock, Instant }

trait Event {
  val eventType: String
  val timestamp: Instant
}

object Event {

  trait JobSpecEvent extends Event
  case class JobSpecCreated(
    job:       JobSpec,
    eventType: String  = "job_created",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobSpecEvent

  case class JobSpecUpdated(
    job:       JobSpec,
    eventType: String  = "job_updated",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobSpecEvent

  case class JobSpecDeleted(
    job:       JobSpec,
    eventType: String  = "job_deleted",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobSpecEvent

  trait JobRunEvent extends Event
  case class JobRunStarted(
    jobRun:    JobRun,
    eventType: String  = "job_run_started",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobRunEvent

  case class JobRunUpdate(
    jobRun:    JobRun,
    eventType: String  = "job_run_updated",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobRunEvent

  case class JobRunFinished(
    jobRun:    JobRun,
    eventType: String  = "job_run_finished",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobRunEvent

  case class JobRunFailed(
    jobRun:    JobRun,
    eventType: String  = "job_run_failed",
    timestamp: Instant = Clock.systemUTC().instant()) extends JobRunEvent

  trait ReconciliationEvent extends Event
  case class ReconciliationFinished(
    eventType: String  = "job_run_failed",
    timestamp: Instant = Clock.systemUTC().instant()) extends ReconciliationEvent

}

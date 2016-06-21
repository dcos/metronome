package dcos.metronome.api

import dcos.metronome.model.JobId

case class ErrorDetail(message: String)
case class UnknownJob(id: JobId, message: String = "Job not found")
case class UnknownSchedule(id: String, message: String = "Schedule not found")
case class UnknownJobRun(jobSpec: JobId, id: String, message: String = "Job Run not found")


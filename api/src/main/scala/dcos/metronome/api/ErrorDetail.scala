package dcos.metronome.api

import mesosphere.marathon.state.PathId

case class ErrorDetail(message: String)
case class UnknownJob(id: PathId, message: String = "Job not found")
case class UnknownSchedule(id: String, message: String = "Schedule not found")
case class UnknownJobRun(jobSpec: PathId, id: String, message: String = "Job Run not found")


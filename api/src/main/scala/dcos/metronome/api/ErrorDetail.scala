package dcos.metronome.api

import play.api.libs.json.{ Format, Json }

case class ErrorDetail(message: String)
case class UnknownJob(id: String, message: String = "Job not found")


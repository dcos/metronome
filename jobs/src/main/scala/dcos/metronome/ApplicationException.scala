package dcos.metronome

import mesosphere.marathon.state.PathId

class ApplicationException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}

case class JobSpecAlreadyExists(id: PathId) extends ApplicationException(s"Job already exists: $id")
case class JobSpecChangeInFlight(id: PathId) extends ApplicationException(s"Job already exists: $id")

case class PersistenceFailed(id: String, reason: String) extends ApplicationException(s"Persistence Failed for: $id Reason: $reason")

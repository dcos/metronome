package dcos.metronome

import dcos.metronome.model.{ JobResult, JobRunId, JobSpec }
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.PathId

class ApplicationException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}

case class JobSpecDoesNotExist(id: PathId) extends ApplicationException(s"JobSpec does not exist: $id")
case class JobSpecAlreadyExists(id: PathId) extends ApplicationException(s"JobSpec already exists: $id")
case class JobSpecChangeInFlight(id: PathId) extends ApplicationException(s"JobSpec change in flight: $id")

case class JobRunDoesNotExist(id: JobRunId) extends ApplicationException(s"JobRun does not exist: $id")
//TODO: add reason of fail
case class JobRunFailed(result: JobResult) extends ApplicationException(s"JobRun execution failed ${result.jobRun.id}")
case class ConcurrentJobRunNotAllowed(spec: JobSpec) extends ApplicationException(s"Concurrent JobRun not allowed ${spec.id}")

case class PersistenceFailed(id: String, reason: String) extends ApplicationException(s"Persistence Failed for: $id Reason: $reason")

case class UnexpectedTaskState(task: Task) extends ApplicationException(s"Encountered unexpected task state in repository: $task")
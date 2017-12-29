package dcos.metronome.queue

import dcos.metronome.model.QueuedJobRunInfo

/**
  * Provides access to the underlying list of tasks in the launch queue.
  *
  */
trait LaunchQueueService {

  def list(): scala.collection.immutable.Seq[QueuedJobRunInfo]

  /**
    * Provides list of tasks in the launch queue grouped by jobspec.id
    * @return map of jobspec -> QueueTaskInfo
    */
  def listGroupByJobId(): Map[String, scala.collection.immutable.Seq[QueuedJobRunInfo]]
}

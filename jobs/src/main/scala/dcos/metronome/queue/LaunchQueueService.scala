package dcos.metronome.queue

import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedTaskInfo

/**
  * Provides access to the underlying list of tasks in the launch queue.
  *
  */
trait LaunchQueueService {

  def list(): Iterable[QueuedTaskInfo]

  /**
    * Provides list of tasks in the launch queue grouped by jobspec.id
    * @return map of jobspec -> QueueTaskInfo
    */
  def listGroupByJobId(): Map[String, scala.collection.immutable.Seq[QueuedTaskInfo]]
}

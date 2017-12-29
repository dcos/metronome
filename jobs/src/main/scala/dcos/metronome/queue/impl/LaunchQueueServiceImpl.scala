package dcos.metronome.queue.impl

import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.launchqueue.LaunchQueue

class LaunchQueueServiceImpl(launchQueue: LaunchQueue) extends LaunchQueueService {

  override def list(): Iterable[LaunchQueue.QueuedTaskInfo] = {
    launchQueue.list
  }

  override def listGroupByJobId(): Map[String, scala.collection.immutable.Seq[LaunchQueue.QueuedTaskInfo]] = {
    launchQueue.list.groupBy(_.runSpec.id.root)
  }
}

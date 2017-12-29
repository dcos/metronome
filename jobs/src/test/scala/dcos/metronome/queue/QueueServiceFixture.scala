package dcos.metronome.queue

import dcos.metronome.queue.impl.LaunchQueueServiceImpl
import mesosphere.marathon.core.launchqueue.LaunchQueue

object QueueServiceFixture {

  def simpleQueueService(): LaunchQueueService = new LaunchQueueServiceImpl(null) {
    override def list(): Iterable[LaunchQueue.QueuedTaskInfo] = List[LaunchQueue.QueuedTaskInfo]()
  }
}

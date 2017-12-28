package dcos.metronome.queue

import dcos.metronome.queue.impl.QueueServiceImpl
import mesosphere.marathon.core.launchqueue.LaunchQueue

object QueueServiceFixture {

  def simpleQueueService(): QueueService = new QueueServiceImpl(null) {
    override def list(): Iterable[LaunchQueue.QueuedTaskInfo] = List[LaunchQueue.QueuedTaskInfo]()
  }
}

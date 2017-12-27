package dcos.metronome.queue

import mesosphere.marathon.core.launchqueue.LaunchQueue

object QueueServiceFixture {

  def simpleQueueService(): QueueService = new QueueService {
    override def list(): Iterable[LaunchQueue.QueuedTaskInfo] = List[LaunchQueue.QueuedTaskInfo]()
  }
}

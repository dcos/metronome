package dcos.metronome.queue

import dcos.metronome.model.QueuedJobRunInfo
import dcos.metronome.queue.impl.LaunchQueueServiceImpl

object QueueServiceFixture {

  def simpleQueueService(): LaunchQueueService = new LaunchQueueServiceImpl(null) {
    override def list(): scala.collection.immutable.Seq[QueuedJobRunInfo] = List[QueuedJobRunInfo]()
  }
}

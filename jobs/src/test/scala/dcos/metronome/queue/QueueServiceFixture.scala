package dcos.metronome
package queue

import dcos.metronome.model.QueuedJobRunInfo
import dcos.metronome.queue.impl.LaunchQueueServiceImpl
import scala.collection.immutable.Seq

object QueueServiceFixture {

  def simpleQueueService(): LaunchQueueService = new LaunchQueueServiceImpl(null) {
    override def list(): Seq[QueuedJobRunInfo] = List[QueuedJobRunInfo]()
  }
}

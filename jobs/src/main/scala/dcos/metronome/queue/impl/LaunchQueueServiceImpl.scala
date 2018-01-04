package dcos.metronome
package queue.impl

import dcos.metronome.jobrun.impl.QueuedJobRunConverter.QueuedTaskInfoToQueuedJobRunInfo
import dcos.metronome.model.QueuedJobRunInfo
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.launchqueue.LaunchQueue

import scala.collection.immutable.Seq

class LaunchQueueServiceImpl(launchQueue: LaunchQueue) extends LaunchQueueService {

  override def list(): Seq[QueuedJobRunInfo] = {
    launchQueue.list.map(_.toModel)
  }

}

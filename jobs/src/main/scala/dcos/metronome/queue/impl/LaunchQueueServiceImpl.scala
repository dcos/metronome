package dcos.metronome
package queue.impl

import dcos.metronome.jobrun.impl.QueuedJobRunConverter.QueuedTaskInfoToQueuedJobRunInfo
import dcos.metronome.model.QueuedJobRunInfo
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.launchqueue.LaunchQueue

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class LaunchQueueServiceImpl(launchQueue: LaunchQueue) extends LaunchQueueService {

  override def list(): Seq[QueuedJobRunInfo] = {
    // timeout is enforced in LaunchQueue
    Await.result(launchQueue.list, Duration.Inf).filter(_.inProgress).map(_.toModel)
  }
}

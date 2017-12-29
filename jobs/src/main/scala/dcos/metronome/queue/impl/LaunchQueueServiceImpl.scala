package dcos.metronome.queue.impl

import dcos.metronome.jobrun.impl.QueuedJobRunConverter.QueuedTaskInfoToQueuedJobRunInfo
import dcos.metronome.model.QueuedJobRunInfo
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.launchqueue.LaunchQueue

class LaunchQueueServiceImpl(launchQueue: LaunchQueue) extends LaunchQueueService {

  override def list(): scala.collection.immutable.Seq[QueuedJobRunInfo] = {
    launchQueue.list.map(_.toModel)
  }

  override def listGroupByJobId(): scala.collection.immutable.Map[String, scala.collection.immutable.Seq[QueuedJobRunInfo]] = {
    this.list().groupBy(_.jobid)
  }
}

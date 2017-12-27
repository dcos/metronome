package dcos.metronome.queue

import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedTaskInfo

trait QueueService {

  def list(): Iterable[QueuedTaskInfo]
}

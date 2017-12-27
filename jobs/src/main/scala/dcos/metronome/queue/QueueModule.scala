package dcos.metronome.queue

import com.softwaremill.macwire.wire
import dcos.metronome.queue.impl.QueueServiceImpl
import mesosphere.marathon.core.launchqueue.LaunchQueue

class QueueModule(launchQueue: LaunchQueue) {

  def queueService = wire[QueueServiceImpl]
}

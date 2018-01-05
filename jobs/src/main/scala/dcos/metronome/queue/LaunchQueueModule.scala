package dcos.metronome
package queue

import com.softwaremill.macwire.wire
import dcos.metronome.queue.impl.LaunchQueueServiceImpl
import mesosphere.marathon.core.launchqueue.LaunchQueue

class LaunchQueueModule(launchQueue: LaunchQueue) {

  def launchQueueService = wire[LaunchQueueServiceImpl]
}

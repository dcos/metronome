package dcos.metronome
package queue

import com.softwaremill.macwire.wire
import dcos.metronome.queue.impl.LaunchQueueServiceImpl
import mesosphere.marathon.core.task.tracker.InstanceTracker

class LaunchQueueModule(instanceTracker: InstanceTracker) {

  def launchQueueService = wire[LaunchQueueServiceImpl]
}

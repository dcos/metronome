package dcos.metronome
package queue

import dcos.metronome.model.QueuedJobRunInfo
import scala.collection.immutable.Seq

/**
  * Provides access to the underlying list of tasks in the launch queue.
  *
  */
trait LaunchQueueService {

  def list(): Seq[QueuedJobRunInfo]

}

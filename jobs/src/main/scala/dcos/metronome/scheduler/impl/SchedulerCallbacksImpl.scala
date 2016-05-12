package dcos.metronome.scheduler.impl

import mesosphere.marathon.SchedulerCallbacks
import mesosphere.marathon.core.election.ElectionService

/** Used by the [[mesosphere.marathon.MarathonScheduler]] after disconnection from Mesos */
class SchedulerCallbacksImpl(electionService: ElectionService) extends SchedulerCallbacks {
  override def disconnected(): Unit = electionService.abdicateLeadership()
}

package mesosphere.marathon

import mesosphere.marathon.core.launchqueue.LaunchQueue.QueuedTaskInfo
import mesosphere.marathon.state.{ RunSpec, Timestamp }

// FIXME: QueuedTaskInfo is package protected to marathon and can't be instantiated for tests ...
class QueuedTaskInfoWrapper(
  runSpec:           RunSpec,
  inProgress:        Boolean,
  tasksLeftToLaunch: Int,
  finalTaskCount:    Int,
  backOffUntil:      Timestamp
) extends QueuedTaskInfo(
  runSpec,
  inProgress,
  tasksLeftToLaunch,
  finalTaskCount,
  backOffUntil
)
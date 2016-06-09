package dcos.metronome.jobrun.impl

import dcos.metronome.jobrun.JobRunService
import mesosphere.marathon.core.task.bus.TaskChangeObservables.TaskChanged
import mesosphere.marathon.core.task.update.TaskUpdateStep

import scala.concurrent.Future

/**
  * Notify the [[JobRunService]] of a task change event.
  */
class NotifyJobRunServiceStep(jobRunService: JobRunService) extends TaskUpdateStep {
  override val name: String = getClass.getSimpleName

  override def processUpdate(taskChanged: TaskChanged): Future[_] = {
    // FIXME: should we only notify of MesosStatusUpdates?
    jobRunService.notifyOfTaskUpdate(taskChanged)
  }

}

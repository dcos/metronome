package dcos.metronome.history

import dcos.metronome.model.JobRun
import mesosphere.marathon.plugin.PathId

import scala.concurrent.Future

/**
  * Manages all finished job runs.
  */
trait HistoryService {

  /**
    * List all available job runs for the given job identifier.
    * @param jobId the id of the job.
    * @return the list of available job runs for this job id.
    */
  def listJobRuns(jobId: PathId): Future[Seq[JobRun]]

}

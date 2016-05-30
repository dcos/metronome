package dcos.metronome.jobrun

import dcos.metronome.model.{ JobRun, JobRunId, JobSpec }
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

/**
  * The JobRunService can be used to start JobRuns, kill started JobRuns and list active JobRuns.
  */
trait JobRunService {

  /**
    * Get all active job runs.
    *
    * @return all active job runs.
    */
  def listRuns(filter: JobRun => Boolean): Future[Seq[StartedJobRun]]

  /**
    * Get all active job runs for the given job spec.
    *
    * @return all active job runs.
    */
  def activeRuns(jobSpecId: PathId): Future[Seq[StartedJobRun]]

  /**
    * Get a specific job run by its id.
    *
    * @param jobRunId the id of the job run.
    * @return the job run with the given id, or none.
    */
  def getJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]]

  /**
    * Start a job by the given job specification.
    * Note: The future returns when the job run was started successfully, not when the job is finished!
    * In order to register a on complete hook, you can use the complete future in StartedJobRun.
    *
    * @param jobSpec the specification to run.
    * @return the started job run
    */
  def startJobRun(jobSpec: JobSpec): Future[StartedJobRun]

  /**
    * Kill a given job run by the given job run id.
    * Note: The future returns when the job kill was initiated successfully, not when the job is killed!
    * In order to register a on complete hook, you can use the complete future in StartedJobRun.
    *
    * @param jobRunId the id of the related job run.
    * @return the job run that was killed, otherwise none.
    */
  def killJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]]
}

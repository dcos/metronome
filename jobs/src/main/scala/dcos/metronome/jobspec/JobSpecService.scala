package dcos.metronome.jobspec

import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

/**
 * The job spec service manages job specifications.
 */
trait JobSpecService {

  /**
   * Create Job Specification.
   * @param jobSpec the job specification to create.
   * @return the created job specification.
   */
  def createJobSpec(jobSpec: JobSpec): Future[JobSpec]

  /**
   * Update a specific Job specification with given id by given update function.
   * @param update the update function to apply
   * @return an updated job spec, if there is a job spec with this, otherwise None.
   */
  def updateJobSpec(id: PathId, update: JobSpec => JobSpec): Future[Option[JobSpec]]

  /**
   * Delete a job spec with the given id.
   * All JobRun's will be killed.
   * @param id the id of the job specification.
   * @return the deleted job specification if existent, otherwise None.
   */
  def deleteJobSpec(id: PathId): Future[Option[JobSpec]]

  /**
   * Get a specific job specification by its id.
   * @param id the id of the jobs specification.
   * @return the job specification if existent, otherwise false.
   */
  def getJobSpec(id: PathId): Future[Option[JobSpec]]

  /**
   * Get all available job specifications.
   * @return all available job specifications
   */
  def getAllJobs: Future[Seq[JobSpec]]

}

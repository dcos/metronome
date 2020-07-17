package dcos.metronome
package jobspec

import dcos.metronome.jobspec.impl.JobSpecServiceActor.Modification
import dcos.metronome.model.{JobId, JobSpec}

import scala.concurrent.Future

/**
  * The job spec service manages job specifications.
  */
trait JobSpecService {

  /**
    * Create Job Specification.
    *
    * @param jobSpec the job specification to create.
    * @return the created job specification.
    */
  def createJobSpec(jobSpec: JobSpec): Future[JobSpec]

  /**
    * Update a specific Job specification with given id by given update function.
    *
    * @param update the update function to apply
    * @return an updated job spec, if there is a job spec with this, otherwise None.
    */
  def updateJobSpec(id: JobId, update: JobSpec => JobSpec): Future[JobSpec]

  /**
    * Delete a job spec with the given id.
    * All JobRun's will be killed.
    *
    * @param id the id of the job specification.
    * @return the deleted job specification
    */
  def deleteJobSpec(id: JobId): Future[JobSpec]

  /**
    * Get a specific job specification by its id.
    *
    * @param id the id of the jobs specification.
    * @return the job specification if existent, otherwise false.
    */
  def getJobSpec(id: JobId): Future[Option[JobSpec]]

  /**
    * Get all available job specifications.
    *
    * @return all available job specifications
    */
  def listJobSpecs(filter: JobSpec => Boolean): Future[Iterable[JobSpec]]

  /**
    * Process a modification in a transaction. No other operation can be performed during the this transaction.
    *
    * @param updater The update method.
    * @return the resulting job specification of the update.
    */
  def transaction(updater: Seq[JobSpec] => Option[Modification]): Future[JobSpec]
}

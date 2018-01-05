package dcos.metronome
package jobinfo

import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.model.JobId

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Queries for extended information about JobSpecs.
  */
trait JobInfoService {

  /**
    * Select one job based on id.
    *
    * @param jobSpecId the id of the job spec.
    * @param selector the selector to select jobs.
    * @param embed the embed options.
    * @param ec the execution context to run on.
    * @return Some(JobInfo) if the id exists and can be selected otherwise false.
    */
  def selectJob(jobSpecId: JobId, selector: JobSpecSelector, embed: Set[Embed])(implicit ec: ExecutionContext): Future[Option[JobInfo]]

  /**
    * Select all jobs based on the given selector.
    *
    * @param selector the selector to select from all jobs.
    * @param embed the embed options to include.
    * @param ec the execution context to run on.
    * @return the selected jobs.
    */
  def selectJobs(selector: JobSpecSelector, embed: Set[Embed])(implicit ec: ExecutionContext): Future[Iterable[JobInfo]]

}

package dcos.metronome
package history

import dcos.metronome.model.{JobId, JobHistory}

import scala.concurrent.Future

trait JobHistoryService {

  def statusFor(jobSpecId: JobId): Future[Option[JobHistory]]

  def list(filter: JobHistory => Boolean): Future[Iterable[JobHistory]]

}

package dcos.metronome.history

import dcos.metronome.model.JobHistory
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

trait JobHistoryService {

  def statusFor(jobSpecId: PathId): Future[Option[JobHistory]]

  def list(filter: JobHistory => Boolean): Future[Iterable[JobHistory]]

}

package dcos.metronome
package history

import dcos.metronome.model.{ JobId, JobHistory }

import scala.concurrent.Future

object JobHistoryServiceFixture {

  def simpleHistoryService(seq: Seq[JobHistory]): JobHistoryService = new JobHistoryService {
    private val lookup: Map[JobId, JobHistory] = seq.map(h => h.jobSpecId -> h)(collection.breakOut)

    override def statusFor(jobSpecId: JobId): Future[Option[JobHistory]] = {
      Future.successful(lookup.get(jobSpecId))
    }

    override def list(filter: JobHistory => Boolean): Future[Iterable[JobHistory]] = {
      Future.successful(lookup.values.filter(filter))
    }
  }
}

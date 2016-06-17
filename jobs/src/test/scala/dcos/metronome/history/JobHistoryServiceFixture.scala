package dcos.metronome.history

import dcos.metronome.model.JobHistory
import mesosphere.marathon.state.PathId

import scala.concurrent.Future

object JobHistoryServiceFixture {

  def simpleHistoryService(seq: Seq[JobHistory]): JobHistoryService = new JobHistoryService {
    private val lookup: Map[PathId, JobHistory] = seq.map(h => h.jobSpecId -> h)(collection.breakOut)

    override def statusFor(jobSpecId: PathId): Future[Option[JobHistory]] = {
      Future.successful(lookup.get(jobSpecId))
    }

    override def list(filter: (JobHistory) => Boolean): Future[Iterable[JobHistory]] = {
      Future.successful(lookup.values.filter(filter))
    }
  }
}

package dcos.metronome.jobspec.impl

import dcos.metronome.model.JobSpec

import scala.concurrent.Future

class JobSpecRepository {

  def all(): Future[Iterable[JobSpec]] = {
    Future.successful(Iterable.empty)
  }

  def get(id: String): Future[Option[JobSpec]] = {
    Future.successful(None)
  }

  def create(id: String, spec: JobSpec): Future[JobSpec] = {
    Future.successful(spec)
  }

  def update(id: String, spec: JobSpec): Future[JobSpec] = {
    Future.successful(spec)
  }

  def delete(id: String): Future[_] = {
    Future.successful(())
  }
}

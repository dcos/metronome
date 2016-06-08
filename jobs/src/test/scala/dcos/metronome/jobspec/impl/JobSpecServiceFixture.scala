package dcos.metronome.jobspec.impl

import dcos.metronome.JobSpecDoesNotExist
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.control.NonFatal

object JobSpecServiceFixture {

  def simpleJobSpecService(): JobSpecService = new JobSpecService {
    val specs = TrieMap.empty[PathId, JobSpec]
    import Future._
    override def getJobSpec(id: PathId): Future[Option[JobSpec]] = successful(specs.get(id))

    override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] = {
      specs += jobSpec.id -> jobSpec
      successful(jobSpec)
    }

    override def updateJobSpec(id: PathId, update: (JobSpec) => JobSpec): Future[JobSpec] = {
      specs.get(id) match {
        case Some(spec) =>
          try {
            val changed = update(spec)
            specs.update(id, changed)
            successful(changed)
          } catch {
            case NonFatal(ex) => failed(ex)
          }
        case None => failed(JobSpecDoesNotExist(id))
      }
    }

    override def listJobSpecs(filter: (JobSpec) => Boolean): Future[Iterable[JobSpec]] = {
      successful(specs.values.filter(filter))
    }

    override def deleteJobSpec(id: PathId): Future[JobSpec] = {
      specs.get(id) match {
        case Some(spec) =>
          specs -= id
          successful(spec)
        case None => failed(JobSpecDoesNotExist(id))
      }
    }
  }
}

package dcos.metronome.repository.impl.kv

import dcos.metronome.model._
import dcos.metronome.repository.impl.kv.marshaller.{ JobHistoryMarshaller, JobRunMarshaller, JobSpecMarshaller }
import mesosphere.marathon.state.PathId
import mesosphere.util.state.PersistentStoreWithNestedPathsSupport

import scala.concurrent.{ ExecutionContext, Future }

case class PathIdPathResolver(basePath: String) extends PathResolver[PathId] {
  lazy val idRegex = """^(.+)$""".r

  override def toPath(id: PathId): String = s"""$basePath/${id.path.mkString(".")}"""
  override def fromPath(path: String): PathId = path match {
    case idRegex(id) => PathId(id.split("[.]").toList, absolute = true)
  }
}

object JobHistoryPathResolver extends PathIdPathResolver("job-histories")
object JobSpecPathResolver extends PathIdPathResolver("job-specs")
object JobRunPathResolver extends PathResolver[JobRunId] {
  override def basePath: String = "job-runs"

  lazy val idRegex = """^([^/]+)/(.+)$""".r

  override def toPath(id: JobRunId): String = s"""$basePath/${id.jobId.path.mkString(".")}/${id.value}"""
  def toPath(jobId: PathId): String = s"""$basePath/${jobId.path.mkString(".")}"""
  override def fromPath(path: String): JobRunId = path match {
    case idRegex(jobId, jobRunId) => JobRunId(PathId(jobId.split("[.]").toList), jobRunId)
  }
}

class ZkJobHistoryRepository(
  store:                    PersistentStoreWithNestedPathsSupport,
  override implicit val ec: ExecutionContext
) extends KeyValueRepository[PathId, JobHistory](
  JobHistoryPathResolver,
  JobHistoryMarshaller,
  store,
  ec
)

class ZkJobRunRepository(
  store:                    PersistentStoreWithNestedPathsSupport,
  override implicit val ec: ExecutionContext
) extends KeyValueRepository[JobRunId, JobRun](
  JobRunPathResolver,
  JobRunMarshaller,
  store,
  ec
) {
  override def ids(): Future[Iterable[JobRunId]] = {
    store.allIds(JobRunPathResolver.basePath).flatMap { parentPaths =>
      parentPaths.foldLeft(Future.successful(List.empty[JobRunId])) {
        case (resultsFuture, parentPath) => resultsFuture.flatMap { jobRunIdsSoFar: List[JobRunId] =>
          store.allIds(s"""${JobRunPathResolver.basePath}/$parentPath""").map { jobRunPaths =>
            jobRunPaths.map(JobRunId(JobSpecPathResolver.fromPath(parentPath), _))
          }.map { jobRunIds => jobRunIds.toList ::: jobRunIdsSoFar }
        }
      }
    }
  }
}

class ZkJobSpecRepository(
  store:                    PersistentStoreWithNestedPathsSupport,
  override implicit val ec: ExecutionContext
) extends KeyValueRepository[PathId, JobSpec](
  JobSpecPathResolver,
  JobSpecMarshaller,
  store,
  ec
) {
  override def create(id: PathId, jobSpec: JobSpec): Future[JobSpec] = {
    val future = store match {
      case s: PersistentStoreWithNestedPathsSupport =>
        s.createPath(JobRunPathResolver.toPath(jobSpec.id)).flatMap { _ =>
          super.create(id, jobSpec)
        }
      case _ => super.create(id, jobSpec)
    }

    future.map(_ => jobSpec)
  }

  override def delete(id: PathId): Future[Boolean] = {
    store match {
      case s: PersistentStoreWithNestedPathsSupport =>
        s.delete(JobRunPathResolver.toPath(id)).flatMap { jobRunDeleteResult =>
          super.delete(id).map(jobRunDeleteResult && _)
        }
      case _ => super.delete(id)
    }
  }
}

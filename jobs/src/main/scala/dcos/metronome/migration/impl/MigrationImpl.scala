package dcos.metronome
package migration.impl

import dcos.metronome.migration.Migration
import dcos.metronome.repository.impl.kv.{ JobHistoryPathResolver, JobRunPathResolver, JobSpecPathResolver }
import dcos.metronome.utils.state.{ PersistentStore, PersistentStoreManagement, PersistentStoreWithNestedPathsSupport }
import org.slf4j.LoggerFactory

import scala.async.Async.{ async, await }
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class MigrationImpl(store: PersistentStore) extends Migration {
  import MigrationImpl._

  override def migrate()(implicit ec: ExecutionContext): Unit = {
    Await.result(initializeStore(), Duration.Inf)
    log.info("Migration successfully applied for version")
  }

  private[this] def initializeStore()(implicit ec: ExecutionContext): Future[Unit] = async {
    store match {
      case store: PersistentStoreManagement with PersistentStoreWithNestedPathsSupport =>
        await(store.initialize())
        await(store.createPath(JobSpecPathResolver.basePath))
        await(store.createPath(JobRunPathResolver.basePath))
        await(store.createPath(JobHistoryPathResolver.basePath))
      case _: PersistentStore =>
        log.info("Unsupported type of persistent store. Not running any migrations.")
        Future.successful(())
    }
  }

}

object MigrationImpl {
  private[migration] val log = LoggerFactory.getLogger(getClass)
}

package dcos.metronome
package migration.impl

import dcos.metronome.migration.Migration
import dcos.metronome.repository.impl.kv.{ JobHistoryPathResolver, JobRunPathResolver, JobSpecPathResolver }
import dcos.metronome.utils.state.{ PersistentStore, PersistentStoreManagement, PersistentStoreWithNestedPathsSupport }
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

class MigrationImpl(store: PersistentStore) extends Migration {
  import MigrationImpl._

  override def migrate(): Unit = {
    Await.result(initializeStore(), Duration.Inf)
    log.info("Migration successfully applied for version")
  }

  private[this] def initializeStore(): Future[Unit] = store match {
    case store: PersistentStoreManagement with PersistentStoreWithNestedPathsSupport =>
      store.initialize()
      store.createPath(JobSpecPathResolver.basePath)
      store.createPath(JobRunPathResolver.basePath)
      store.createPath(JobHistoryPathResolver.basePath)
    case _: PersistentStore => Future.successful(())
  }

}

object MigrationImpl {
  private[migration] val log = LoggerFactory.getLogger(getClass)
}

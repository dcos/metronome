package dcos.metronome.migration.impl

import dcos.metronome.migration.Migration
import mesosphere.util.state.{ PersistentStore, PersistentStoreManagement }
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
    case manager: PersistentStoreManagement => manager.initialize()
    case _: PersistentStore                 => Future.successful(())
  }

}

object MigrationImpl {
  private[migration] val log = LoggerFactory.getLogger(getClass)
}

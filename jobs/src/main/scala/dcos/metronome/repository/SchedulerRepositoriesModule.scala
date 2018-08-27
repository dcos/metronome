package dcos.metronome
package repository

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import com.twitter.common.quantity.{ Amount, Time }
import com.twitter.common.zookeeper.ZooKeeperClient
import dcos.metronome.migration.Migration
import dcos.metronome.migration.impl.MigrationImpl
import dcos.metronome.scheduler.SchedulerConfig
import dcos.metronome.utils.state.PersistentStore
import mesosphere.marathon.core.base.{ ActorsModule, LifecycleState }
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.storage.repository.{ FrameworkIdRepository, GroupRepository, InstanceRepository }
import mesosphere.marathon.storage.{ StorageConfig, StorageModule }
import org.apache.zookeeper.KeeperException
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, ExecutionContext, Future }

class SchedulerRepositoriesModule(metrics: Metrics, config: SchedulerConfig, repositoryModule: RepositoryModule, lifecycleState: LifecycleState, actorsModule: ActorsModule, actorSystem: ActorSystem) {
  import SchedulerRepositoriesModule._

  lazy val zk: ZooKeeperClient = {
    require(
      config.zkSessionTimeout.toMillis < Integer.MAX_VALUE,
      "ZooKeeper timeout too large!")

    val client = new ZooKeeperLeaderElectionClient(
      Amount.of(config.zkSessionTimeout.toMillis.toInt, Time.MILLISECONDS),
      config.zkHostAddresses.asJavaCollection)

    // Marathon can't do anything useful without a ZK connection
    // so we wait to proceed until one is available
    var connectedToZk = false

    while (!connectedToZk) {
      try {
        log.info("Connecting to ZooKeeper...")
        client.get
        connectedToZk = true
      } catch {
        case _: Throwable =>
          log.warn("Unable to connect to ZooKeeper, retrying...")
      }
    }
    client
  }

  private[this] lazy val persistentStore: PersistentStore = repositoryModule.zkStore

  lazy val storageConfig = StorageConfig(config.scallopConf, lifecycleState)
  lazy val storageModule: StorageModule = StorageModule(metrics, config.scallopConf, lifecycleState)(actorsModule.materializer, ExecutionContext.global, actorSystem.scheduler, actorSystem)

  lazy val instanceRepository: InstanceRepository = storageModule.instanceRepository
  lazy val groupRepository: GroupRepository = storageModule.groupRepository

  lazy val frameworkIdRepository: FrameworkIdRepository = storageModule.frameworkIdRepository

  lazy val migration: Migration = new MigrationImpl(persistentStore)
}

object SchedulerRepositoriesModule {
  val log: Logger = LoggerFactory.getLogger(getClass)

  class ZooKeeperLeaderElectionClient(
    sessionTimeout:   Amount[Integer, Time],
    zooKeeperServers: java.lang.Iterable[InetSocketAddress])
      extends ZooKeeperClient(sessionTimeout, zooKeeperServers) {
    import scala.concurrent.duration._

    override def shouldRetry(e: KeeperException): Boolean = {
      log.error("Got ZooKeeper exception", e)
      log.error("Committing suicide to avoid invalidating ZooKeeper state")

      val f = Future {
        // scalastyle:off magic.number
        Runtime.getRuntime.exit(9)
        // scalastyle:on
      }(scala.concurrent.ExecutionContext.global)

      try {
        Await.result(f, 5.seconds)
      } catch {
        case _: Throwable =>
          log.error("Finalization failed, killing JVM.")
          // scalastyle:off magic.number
          Runtime.getRuntime.halt(1)
        // scalastyle:on
      }

      false
    }
  }

}

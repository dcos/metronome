package dcos.metronome.repository

import java.net.InetSocketAddress
import java.util.UUID

import com.twitter.common.quantity.{ Amount, Time }
import com.twitter.common.zookeeper.ZooKeeperClient
import dcos.metronome.migration.Migration
import dcos.metronome.migration.impl.MigrationImpl
import dcos.metronome.{ JobsConfig, MetricsModule }
import mesosphere.marathon.Protos.MarathonTask
import mesosphere.marathon.state.{ AppDefinition, AppRepository, EntityStoreCache, Group, GroupRepository, MarathonStore, MarathonTaskState, TaskRepository }
import mesosphere.util.state.{ FrameworkId, PersistentStore }
import org.apache.zookeeper.KeeperException
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, Future }

// FIXME: use a dedicated conf object
class SchedulerRepositoriesModule(config: JobsConfig, repositoryModule: RepositoryModule) {
  import SchedulerRepositoriesModule._

  private[this] lazy val metricsModule = new MetricsModule()
  private[this] lazy val metrics = metricsModule.metrics
  private[this] lazy val marathonConf = config.scallopConf

  lazy val zk: ZooKeeperClient = {
    require(
      marathonConf.zooKeeperSessionTimeout() < Integer.MAX_VALUE,
      "ZooKeeper timeout too large!"
    )

    val client = new ZooKeeperLeaderElectionClient(
      Amount.of(marathonConf.zooKeeperSessionTimeout().toInt, Time.MILLISECONDS),
      config.zkHostAddresses.asJavaCollection
    )

    // Marathon can't do anything useful without a ZK connection
    // so we wait to proceed until one is available
    var connectedToZk = false

    while (!connectedToZk) {
      try {
        log.info("Connecting to ZooKeeper...")
        client.get
        connectedToZk = true
      } catch {
        case t: Throwable =>
          log.warn("Unable to connect to ZooKeeper, retrying...")
      }
    }
    client
  }

  private[this] lazy val persistentStore: PersistentStore = repositoryModule.zkStore

  lazy val taskStore = new MarathonStore[MarathonTaskState](
    persistentStore,
    metrics,
    prefix = "task:",
    newState = () => MarathonTaskState(MarathonTask.newBuilder().setId(UUID.randomUUID().toString).build())
  )
  lazy val taskRepository: TaskRepository = new TaskRepository(taskStore, metrics)

  // FIXME (wiring): we shouldn't need the groupRepo
  lazy val groupStore = new MarathonStore[Group](
    persistentStore,
    metrics,
    prefix = "group:",
    newState = () => Group.empty
  )
  lazy val groupRepository: GroupRepository = new GroupRepository(
    groupStore,
    marathonConf.zooKeeperMaxVersions.get,
    metrics
  )

  lazy val frameworkIdStore = {
    val newState = () => new FrameworkId(UUID.randomUUID().toString)
    val prefix = "framework:"
    val marathonStore = new MarathonStore[FrameworkId](persistentStore, metrics, newState, prefix)
    if (marathonConf.storeCache()) new EntityStoreCache[FrameworkId](marathonStore) else marathonStore
  }

  lazy val appStore = new MarathonStore[AppDefinition](
    persistentStore,
    metrics,
    prefix = "app:",
    newState = () => AppDefinition.apply()
  )
  lazy val appRepository: AppRepository = new AppRepository(appStore, marathonConf.zooKeeperMaxVersions.get, metrics)

  lazy val migration: Migration = new MigrationImpl(persistentStore)
}

object SchedulerRepositoriesModule {
  val log = LoggerFactory.getLogger(getClass)

  class ZooKeeperLeaderElectionClient(
    sessionTimeout:   Amount[Integer, Time],
    zooKeeperServers: java.lang.Iterable[InetSocketAddress]
  )
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

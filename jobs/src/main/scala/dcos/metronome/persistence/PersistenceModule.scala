package dcos.metronome.persistence

import java.net.InetSocketAddress
import java.util.UUID

import com.twitter.common.quantity.{ Amount, Time }
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.util.JavaTimer
import com.twitter.zk.{ AuthInfo, NativeConnector, ZkClient }
import dcos.metronome.migration.Migration
import dcos.metronome.migration.impl.MigrationImpl
import mesosphere.marathon.AllConf
import mesosphere.marathon.Protos.MarathonTask
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.state.{ AppDefinition, AppRepository, EntityStoreCache, Group, GroupRepository, MarathonStore, MarathonTaskState, TaskRepository }
import mesosphere.util.state.zk.{ CompressionConf, ZKStore }
import mesosphere.util.state.{ FrameworkId, PersistentStore }
import org.apache.zookeeper.KeeperException
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, Future }

// FIXME: use a dedicated conf object
class PersistenceModule(conf: AllConf, metrics: Metrics) {
  import PersistenceModule._

  lazy val zk: ZooKeeperClient = {
    require(
      conf.zooKeeperSessionTimeout() < Integer.MAX_VALUE,
      "ZooKeeper timeout too large!"
    )

    val client = new ZooKeeperLeaderElectionClient(
      Amount.of(conf.zooKeeperSessionTimeout().toInt, Time.MILLISECONDS),
      conf.zooKeeperHostAddresses.asJavaCollection
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

  private[this] lazy val persistentStore: PersistentStore = {
    import com.twitter.util.TimeConversions._
    val sessionTimeout = conf.zooKeeperSessionTimeout().millis

    val authInfo = (conf.zkUsername, conf.zkPassword) match {
      case (Some(user), Some(pass)) => Some(AuthInfo.digest(user, pass))
      case _                        => None
    }

    val connector = NativeConnector(conf.zkHosts, None, sessionTimeout, new JavaTimer(isDaemon = true), authInfo)

    val client = ZkClient(connector)
      .withAcl(conf.zkDefaultCreationACL.asScala)
      .withRetries(3)
    val compressionConf = CompressionConf(conf.zooKeeperCompressionEnabled(), conf.zooKeeperCompressionThreshold())
    new ZKStore(client, client(conf.zooKeeperStatePath), compressionConf)
  }

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
  lazy val groupRepository: GroupRepository = new GroupRepository(groupStore, conf.zooKeeperMaxVersions.get, metrics)

  lazy val frameworkIdStore = {
    val newState = () => new FrameworkId(UUID.randomUUID().toString)
    val prefix = "framework:"
    val marathonStore = new MarathonStore[FrameworkId](persistentStore, metrics, newState, prefix)
    if (conf.storeCache()) new EntityStoreCache[FrameworkId](marathonStore) else marathonStore
  }

  lazy val appStore = new MarathonStore[AppDefinition](
    persistentStore,
    metrics,
    prefix = "app:",
    newState = () => AppDefinition.apply()
  )
  lazy val appRepository: AppRepository = new AppRepository(appStore, maxVersions = conf.zooKeeperMaxVersions.get, metrics)

  lazy val migration: Migration = new MigrationImpl(persistentStore)
}

object PersistenceModule {
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

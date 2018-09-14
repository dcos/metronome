package dcos.metronome
package repository.impl.kv

import java.net.InetSocketAddress

import com.twitter.zk.AuthInfo
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.data.ACL

import scala.concurrent.duration.FiniteDuration

trait ZkConfig {
  import mesosphere.marathon.ZookeeperConf._

  /**
    * *
    * ZooKeeper URL for storing state. Format: zk://host1:port1,host2:port2,.../path
    */
  def zkURL: String

  /**
    * *
    * The timeout for ZooKeeper in milliseconds.
    */
  def zkTimeout: FiniteDuration

  /**
    * The timeout for ZooKeeper sessions in milliseconds
    */
  def zkSessionTimeout: FiniteDuration

  /**
    * Enable compression of zk nodes, if the size of the node is bigger than the configured threshold.
    */
  def zkCompressionEnabled: Boolean

  /**
    * Threshold in bytes, when compression is applied to the ZooKeeper node.
    */
  def zkCompressionThreshold: Long

  def zkStatePath: String = s"$zkPath/state"
  def zkLeaderPath: String = s"$zkPath/leader"

  lazy val zkHosts = zkURL match { case ZkUrlPattern(_, _, server, _) => server }
  lazy val zkPath = zkURL match { case ZkUrlPattern(_, _, _, path) => path }
  lazy val zkUsername = zkURL match { case ZkUrlPattern(u, _, _, _) => Option(u) }
  lazy val zkPassword = zkURL match { case ZkUrlPattern(_, p, _, _) => Option(p) }

  lazy val zkAuthInfo = (zkUsername, zkPassword) match {
    case (Some(user), Some(pass)) => Some(AuthInfo.digest(user, pass))
    case _                        => None
  }

  import scala.collection.JavaConverters._
  lazy val zkDefaultCreationACL: Seq[ACL] = (zkUsername, zkPassword) match {
    case (Some(_), Some(_)) => ZooDefs.Ids.CREATOR_ALL_ACL.asScala.to[Seq]
    case _                  => ZooDefs.Ids.OPEN_ACL_UNSAFE.asScala.to[Seq]
  }

  def zkHostAddresses: Seq[InetSocketAddress] =
    (for (s <- zkHosts.split(",")) yield {
      val splits = s.split(":")
      require(splits.length == 2, "expected host:port for zk servers")
      new InetSocketAddress(splits(0), splits(1).toInt)
    }).to[Seq]

}

object ZkConfig {
  import scala.concurrent.duration._

  val DEFAULT_ZK_URL: String = "zk://localhost:2181/metronome"
  val DEFAULT_ZK_TIMEOUT: FiniteDuration = 10.seconds
  val DEFAULT_ZK_SESSION_TIMEOUT: FiniteDuration = 10.seconds
  val DEFAULT_ZK_COMPRESSION_ENABLED: Boolean = true
  val DEFAULT_ZK_COMPRESSION_THRESHOLD: Long = 64 * 1024 // 64 KB
}

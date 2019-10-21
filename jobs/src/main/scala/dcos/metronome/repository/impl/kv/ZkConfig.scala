package dcos.metronome
package repository.impl.kv

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

  lazy val zkPath = zkURL match { case ZkUrlPattern(_, _, _, path) => path }
}

object ZkConfig {
  import scala.concurrent.duration._

  val DEFAULT_ZK_URL: String = "zk://localhost:2181/metronome"
  val DEFAULT_ZK_TIMEOUT: FiniteDuration = 10.seconds
  val DEFAULT_ZK_SESSION_TIMEOUT: FiniteDuration = 10.seconds
  val DEFAULT_ZK_COMPRESSION_ENABLED: Boolean = true
  val DEFAULT_ZK_COMPRESSION_THRESHOLD: Long = 64 * 1024 // 64 KB
}

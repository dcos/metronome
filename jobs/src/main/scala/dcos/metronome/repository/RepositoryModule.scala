package dcos.metronome
package repository

import com.twitter.util.JavaTimer
import com.twitter.zk.{ NativeConnector, ZNode, ZkClient }
import dcos.metronome.model._
import dcos.metronome.repository.impl.kv.{ ZkConfig, ZkJobHistoryRepository, ZkJobRunRepository, ZkJobSpecRepository }
import mesosphere.util.state.zk.{ CompressionConf, ZKStore }

import scala.concurrent.ExecutionContext

class RepositoryModule(config: ZkConfig) {
  val ec = ExecutionContext.global

  private[this] val zkClient: ZkClient = {
    import com.twitter.util.TimeConversions._

    val connector = NativeConnector(
      config.zkHosts,
      None,
      config.zkSessionTimeout.toMillis.millis,
      new JavaTimer(isDaemon = true),
      config.zkAuthInfo
    )

    ZkClient(connector)
      .withAcl(config.zkDefaultCreationACL)
      .withRetries(3)
  }

  private[this] val zkRoot: ZNode = ZNode(zkClient, config.zkStatePath)

  val zkStore: ZKStore = new ZKStore(
    zkClient,
    zkRoot,
    CompressionConf(config.zkCompressionEnabled, config.zkCompressionThreshold)
  )

  def jobSpecRepository: Repository[JobId, JobSpec] = new ZkJobSpecRepository(zkStore, ec)

  def jobRunRepository: Repository[JobRunId, JobRun] = new ZkJobRunRepository(zkStore, ec)

  def jobHistoryRepository: Repository[JobId, JobHistory] = new ZkJobHistoryRepository(zkStore, ec)
}

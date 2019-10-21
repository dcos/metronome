package dcos.metronome
package repository

import akka.actor.ActorSystem
import dcos.metronome.migration.Migration
import dcos.metronome.migration.impl.MigrationImpl
import dcos.metronome.model.{ JobHistory, JobId, JobRun, JobRunId, JobSpec }
import dcos.metronome.repository.impl.kv.{ ZkJobHistoryRepository, ZkJobRunRepository, ZkJobSpecRepository }
import dcos.metronome.scheduler.SchedulerConfig
import dcos.metronome.utils.state.{ CompressionConf, ZKStore }
import mesosphere.marathon.core.base.{ ActorsModule, CrashStrategy, LifecycleState }
import mesosphere.marathon.core.storage.store.impl.zk.RichCuratorFramework
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.storage.repository.{ FrameworkIdRepository, GroupRepository, InstanceRepository }
import mesosphere.marathon.storage.{ StorageConfig, StorageModule }

import scala.concurrent.ExecutionContext

class SchedulerRepositoriesModule(metrics: Metrics, config: SchedulerConfig, lifecycleState: LifecycleState, actorsModule: ActorsModule, actorSystem: ActorSystem, crashStrategy: CrashStrategy) {

  private[this] val ec = ExecutionContext.global

  private[this] val zkRootPath = config.zkStatePath

  lazy val curatorFramework: RichCuratorFramework = StorageConfig.curatorFramework(config.scallopConf, crashStrategy, lifecycleState)

  val zkStore: ZKStore = new ZKStore(
    curatorFramework,
    zkRootPath,
    CompressionConf(config.zkCompressionEnabled, config.zkCompressionThreshold))

  def jobSpecRepository: Repository[JobId, JobSpec] = new ZkJobSpecRepository(zkStore, ec)

  def jobRunRepository: Repository[JobRunId, JobRun] = new ZkJobRunRepository(zkStore, ec)

  def jobHistoryRepository: Repository[JobId, JobHistory] = new ZkJobHistoryRepository(zkStore, ec)

  lazy val storageConfig = StorageConfig(config.scallopConf, Some(curatorFramework))
  lazy val storageModule: StorageModule = StorageModule(metrics, storageConfig, config.scallopConf.mesosBridgeName())(actorsModule.materializer, ExecutionContext.global, actorSystem.scheduler, actorSystem)

  lazy val instanceRepository: InstanceRepository = storageModule.instanceRepository
  lazy val groupRepository: GroupRepository = storageModule.groupRepository

  lazy val frameworkIdRepository: FrameworkIdRepository = storageModule.frameworkIdRepository

  lazy val migration: Migration = new MigrationImpl(zkStore)

}

object SchedulerRepositoriesModule {

}

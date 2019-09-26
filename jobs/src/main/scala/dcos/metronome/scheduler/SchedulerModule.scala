package dcos.metronome
package scheduler

import java.time.{ Clock, OffsetDateTime }
import java.util.concurrent.Executors

import akka.{ Done, NotUsed }
import akka.actor.{ ActorRefFactory, ActorSystem, Cancellable }
import akka.event.EventStream
import akka.stream.scaladsl.Source
import dcos.metronome.repository.SchedulerRepositoriesModule
import dcos.metronome.scheduler.impl.{ NotifyOfTaskStateOperationStep, PeriodicOperationsImpl, ReconciliationActor }
import mesosphere.marathon._
import mesosphere.marathon.core.async.ExecutionContexts
import mesosphere.marathon.core.base.{ ActorsModule, CrashStrategy, LifecycleState }
import mesosphere.marathon.core.deployment.DeploymentPlan
import mesosphere.marathon.core.election.{ ElectionModule, ElectionService }
import mesosphere.marathon.core.flow.FlowModule
import mesosphere.marathon.core.group.GroupManager
import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.core.instance.update.InstanceChangeHandler
import mesosphere.marathon.core.launcher.{ LauncherModule, OfferProcessor }
import mesosphere.marathon.core.launchqueue.LaunchQueueModule
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.core.matcher.base.OfferMatcher
import mesosphere.marathon.core.matcher.manager.OfferMatcherManagerModule
import mesosphere.marathon.core.plugin.PluginModule
import mesosphere.marathon.core.pod.PodDefinition
import mesosphere.marathon.core.storage.store.impl.zk.RichCuratorFramework
import mesosphere.marathon.core.task.jobs.TaskJobsModule
import mesosphere.marathon.core.task.termination.{ KillService, TaskTerminationModule }
import mesosphere.marathon.core.task.tracker._
import mesosphere.marathon.core.task.update.TaskStatusUpdateProcessor
import mesosphere.marathon.core.task.update.impl.TaskStatusUpdateProcessorImpl
import mesosphere.marathon.core.task.update.impl.steps.ContinueOnErrorStep
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.state.{ AbsolutePathId, AppDefinition, Group, RootGroup, RunSpec, Timestamp }
import mesosphere.marathon.storage.StorageConfig
import mesosphere.marathon.storage.repository.{ GroupRepository, InstanceRepository }
import mesosphere.util.state._
import org.apache.mesos.Protos.FrameworkID

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Random

class SchedulerModule(
  metrics:           Metrics,
  config:            SchedulerConfig,
  actorSystem:       ActorSystem,
  clock:             Clock,
  persistenceModule: SchedulerRepositoriesModule,
  pluginModule:      PluginModule,
  lifecycleState:    LifecycleState,
  crashStrategy:     CrashStrategy,
  actorsModule:      ActorsModule) {

  private[this] lazy val scallopConf: AllConf = config.scallopConf

  private[this] lazy val random = Random

  private[this] lazy val eventBus: EventStream = actorSystem.eventStream

  lazy val schedulerDriverHolder: MarathonSchedulerDriverHolder = new MarathonSchedulerDriverHolder

  private[this] lazy val hostPort: String = config.hostnameWithPort

  private[this] val electionExecutor = Executors.newSingleThreadExecutor()

  // Initialize Apache Curator Framework (wrapped in [[RichCuratorFramework]] and connect/sync with the storage
  // for an underlying Zookeeper storage.
  lazy val richCuratorFramework: RichCuratorFramework = StorageConfig.curatorFramework(scallopConf, crashStrategy, lifecycleState)

  lazy val electionModule: ElectionModule = new ElectionModule(
    metrics,
    scallopConf,
    actorSystem,
    eventBus,
    hostPort,
    crashStrategy,
    richCuratorFramework.client.usingNamespace(null), // using non-namespaced client for leader-election
    ExecutionContext.fromExecutor(electionExecutor))

  val leadershipModule: LeadershipModule = {
    val actorRefFactory: ActorRefFactory = actorsModule.actorRefFactory

    LeadershipModule(actorRefFactory)
  }

  val instanceRepository: InstanceRepository = persistenceModule.instanceRepository

  val groupRepository: GroupRepository = persistenceModule.groupRepository

  lazy val instanceTrackerModule: InstanceTrackerModule = {
    val updateSteps: Seq[InstanceChangeHandler] = Seq(
      ContinueOnErrorStep(new NotifyOfTaskStateOperationStep(eventBus, clock)))

    new InstanceTrackerModule(
      metrics,
      clock,
      scallopConf,
      leadershipModule,
      instanceRepository,
      groupRepository,
      updateSteps,
      crashStrategy)(actorsModule.materializer)
  }

  private[this] lazy val offerMatcherManagerModule = new OfferMatcherManagerModule(
    // infrastructure
    metrics, clock, random, scallopConf,
    leadershipModule,
    () => scheduler.getLocalRegion)(actorsModule.materializer)

  private[this] val groupManager: GroupManager = {
    FakeRootGroupManager
  }

  private[this] lazy val launcherModule: LauncherModule = {
    val instanceTracker: InstanceTracker = instanceTrackerModule.instanceTracker
    val offerMatcher: OfferMatcher = offerMatcherManagerModule.globalOfferMatcher

    new LauncherModule(
      metrics,
      scallopConf,
      instanceTracker,
      schedulerDriverHolder,
      offerMatcher,
      pluginModule.pluginManager,
      groupManager)(clock)
  }

  private[this] object FakeRootGroupManager extends GroupManager {
    override def rootGroup(): RootGroup = ???

    override def rootGroupOption(): Option[RootGroup] = ???

    override def versions(id: AbsolutePathId): Source[Timestamp, NotUsed] = ???

    override def appVersions(id: AbsolutePathId): Source[OffsetDateTime, NotUsed] = ???

    override def appVersion(id: AbsolutePathId, version: OffsetDateTime): Future[Option[AppDefinition]] = ???

    override def podVersions(id: AbsolutePathId): Source[OffsetDateTime, NotUsed] = ???

    override def podVersion(id: AbsolutePathId, version: OffsetDateTime): Future[Option[PodDefinition]] = ???

    override def group(id: AbsolutePathId): Option[Group] = ???

    override def group(id: AbsolutePathId, version: Timestamp): Future[Option[Group]] = ???

    override def runSpec(id: AbsolutePathId): Option[RunSpec] = ???

    override def app(id: AbsolutePathId): Option[AppDefinition] = ???

    override def apps(ids: Set[AbsolutePathId]): Map[AbsolutePathId, Option[AppDefinition]] = ???

    override def pod(id: AbsolutePathId): Option[PodDefinition] = ???

    override def updateRootEither[T](id: AbsolutePathId, fn: RootGroup => Future[Either[T, RootGroup]], version: Timestamp, force: Boolean, toKill: Map[AbsolutePathId, Seq[Instance]]): Future[Either[T, DeploymentPlan]] = ???

    override def patchRoot(fn: RootGroup => RootGroup): Future[Done] = ???

    override def invalidateAndRefreshGroupCache(): Future[Done] = ???

    override def invalidateGroupCache(): Future[Done] = ???
  }

  private[this] lazy val taskTerminationModule: TaskTerminationModule = new TaskTerminationModule(
    instanceTrackerModule,
    leadershipModule,
    schedulerDriverHolder,
    config.taskKillConfig,
    metrics,
    clock,
    actorSystem)
  lazy val killService: KillService = taskTerminationModule.taskKillService

  private val frameworkIdPromise = Promise[FrameworkID]
  private val initialFrameworkInfo = frameworkIdPromise.future
    .map { frameworkId =>
      MarathonSchedulerDriver.newFrameworkInfo(Some(frameworkId), scallopConf, scallopConf)
    }(ExecutionContexts.callerThread)

  private[this] lazy val scheduler: MarathonScheduler = {
    val instanceTracker: InstanceTracker = instanceTrackerModule.instanceTracker
    val offerProcessor: OfferProcessor = launcherModule.offerProcessor
    val taskStatusProcessor: TaskStatusUpdateProcessor = new TaskStatusUpdateProcessorImpl(
      metrics, clock, instanceTracker, schedulerDriverHolder, killService, eventBus)
    val leaderInfo = config.mesosLeaderUiUrl match {
      case someUrl @ Some(_) => ConstMesosLeaderInfo(someUrl)
      case None              => new MutableMesosLeaderInfo
    }

    new MarathonScheduler(
      eventBus,
      offerProcessor,
      taskStatusProcessor,
      persistenceModule.frameworkIdRepository,
      leaderInfo,
      scallopConf,
      crashStrategy,
      frameworkIdPromise)
  }

  val schedulerDriverFactory: SchedulerDriverFactory = new MesosSchedulerDriverFactory(
    holder = schedulerDriverHolder,
    config = scallopConf,
    httpConfig = scallopConf,
    frameworkIdRepository = persistenceModule.frameworkIdRepository,
    instanceRepository = instanceRepository,
    crashStrategy = crashStrategy,
    scheduler = scheduler)(actorsModule.materializer)

  val prePostDriverCallbacks: Seq[PrePostDriverCallback] = Seq(
    persistenceModule.instanceRepository,
    persistenceModule.frameworkIdRepository,
    persistenceModule.groupRepository).collect {
      case l: PrePostDriverCallback => l
    }

  val periodicOperations: PeriodicOperations = new PeriodicOperationsImpl()

  val flowModule = new FlowModule(leadershipModule)
  // make sure launch tokens get initialized
  flowModule.refillOfferMatcherManagerLaunchTokens(
    scallopConf, offerMatcherManagerModule.subOfferMatcherManager)

  leadershipModule.startWhenLeader(
    props = ReconciliationActor.props(schedulerDriverHolder, instanceTrackerModule.instanceTracker, config),
    name = "reconciliationActor")

  val taskJobsModule = new TaskJobsModule(config.scallopConf, leadershipModule, clock)

  lazy val electionService: ElectionService = electionModule.service

  /** Combine offersWanted state from multiple sources. */
  private[this] lazy val offersWanted: Source[Boolean, Cancellable] = offerMatcherManagerModule.globalOfferMatcherWantsOffers

  val launchQueueModule = new LaunchQueueModule(
    config = scallopConf,
    reviveConfig = scallopConf,
    metrics = metrics,
    leadershipModule = leadershipModule,
    clock = clock,
    subOfferMatcherManager = offerMatcherManagerModule.subOfferMatcherManager,
    driverHolder = schedulerDriverHolder,
    instanceTracker = instanceTrackerModule.instanceTracker,
    eventStream = eventBus,
    groupManager = groupManager,
    //    maybeOfferReviver = flowModule.maybeOfferReviver(metrics, clock, scallopConf, eventBus, offersWanted, schedulerDriverHolder),
    //    taskTracker = instanceTrackerModule.instanceTracker,
    taskOpFactory = launcherModule.taskOpFactory,
    localRegion = () => scheduler.getLocalRegion,
    initialFrameworkInfo = initialFrameworkInfo)(actorsModule.materializer, ExecutionContext.global)

  taskJobsModule.expungeOverdueLostTasks(instanceTrackerModule.instanceTracker)

  taskJobsModule.handleOverdueTasks(
    instanceTrackerModule.instanceTracker,
    killService,
    metrics)
}

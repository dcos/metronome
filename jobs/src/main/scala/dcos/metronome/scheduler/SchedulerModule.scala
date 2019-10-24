package dcos.metronome
package scheduler

import java.time.Clock
import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.{ActorRefFactory, ActorSystem, Cancellable}
import akka.event.EventStream
import akka.stream.scaladsl.Source
import dcos.metronome.model.JobRunId
import dcos.metronome.repository.SchedulerRepositoriesModule
import dcos.metronome.scheduler.impl.{MetronomeExpungeStrategy, NotifyLaunchQueueStep, NotifyOfTaskStateOperationStep, PeriodicOperationsImpl, ReconciliationActor}
import mesosphere.marathon._
import mesosphere.marathon.core.async.ExecutionContexts
import mesosphere.marathon.core.base.{ActorsModule, CrashStrategy, LifecycleState}
import mesosphere.marathon.core.election.{ElectionModule, ElectionService}
import mesosphere.marathon.core.flow.FlowModule
import mesosphere.marathon.core.group.GroupManager
import mesosphere.marathon.core.instance.update.InstanceChangeHandler
import mesosphere.marathon.core.launcher.{LauncherModule, OfferProcessor}
import mesosphere.marathon.core.launchqueue.LaunchQueueModule
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.core.matcher.base.OfferMatcher
import mesosphere.marathon.core.matcher.manager.OfferMatcherManagerModule
import mesosphere.marathon.core.plugin.PluginModule
import mesosphere.marathon.core.task.jobs.TaskJobsModule
import mesosphere.marathon.core.task.termination.{KillService, TaskTerminationModule}
import mesosphere.marathon.core.task.tracker._
import mesosphere.marathon.core.task.update.TaskStatusUpdateProcessor
import mesosphere.marathon.core.task.update.impl.TaskStatusUpdateProcessorImpl
import mesosphere.marathon.core.task.update.impl.steps.{ContinueOnErrorStep, NotifyRateLimiterStepImpl}
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.state.{AbsolutePathId, RunSpec}
import mesosphere.marathon.storage.repository.{GroupRepository, InstanceRepository}
import mesosphere.util.state._
import org.apache.mesos.Protos.FrameworkID
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Promise}
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

  private[this] val log = LoggerFactory.getLogger(getClass)

  private[this] lazy val scallopConf: AllConf = config.scallopConf

  private[this] lazy val random = Random

  private[this] lazy val eventBus: EventStream = actorSystem.eventStream

  lazy val schedulerDriverHolder: MarathonSchedulerDriverHolder = new MarathonSchedulerDriverHolder

  private[this] lazy val hostPort: String = config.hostnameWithPort

  private[this] val electionExecutor = Executors.newSingleThreadExecutor()

  lazy val electionModule: ElectionModule = new ElectionModule(
    metrics,
    scallopConf,
    actorSystem,
    eventBus,
    hostPort,
    crashStrategy,
    persistenceModule.curatorFramework.client.usingNamespace(null), // using non-namespaced client for leader-election
    ExecutionContext.fromExecutor(electionExecutor))

  val leadershipModule: LeadershipModule = {
    val actorRefFactory: ActorRefFactory = actorsModule.actorRefFactory

    LeadershipModule(actorRefFactory)
  }

  val instanceRepository: InstanceRepository = persistenceModule.instanceRepository

  val groupRepository: GroupRepository = persistenceModule.groupRepository

  lazy val instanceTrackerModule: InstanceTrackerModule = {
    val updateSteps: Seq[InstanceChangeHandler] = Seq(
      ContinueOnErrorStep(new NotifyLaunchQueueStep(() => launchQueueModule.launchQueue)),
      ContinueOnErrorStep(new NotifyRateLimiterStepImpl(() => launchQueueModule.launchQueue)),
      ContinueOnErrorStep(new NotifyOfTaskStateOperationStep(eventBus, clock)))

    new InstanceTrackerModule(
      metrics,
      clock,
      scallopConf,
      leadershipModule,
      instanceRepository,
      groupRepository,
      updateSteps,
      crashStrategy,
      expungeStrategy = MetronomeExpungeStrategy)(actorsModule.materializer)
  }

  private[this] lazy val offerMatcherManagerModule = new OfferMatcherManagerModule(
    // infrastructure
    metrics, clock, random, scallopConf,
    leadershipModule,
    () => scheduler.getLocalRegion)(actorsModule.materializer)

  private[this] val runSpecProvider = RunSpecProvider

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
      runSpecProvider)(clock)
  }

  private[this] object RunSpecProvider extends GroupManager.RunSpecProvider with GroupManager.EnforceRoleSettingProvider {

    override def runSpec(id: AbsolutePathId): Option[RunSpec] = {
      import dcos.metronome.utils.glue.MarathonImplicits._

      log.error(s"Need to provide runSpec for ${id}", new RuntimeException())
      val jobId = JobRunId(id)
      Await.result(persistenceModule.jobRunRepository.get(jobId), Duration(30, TimeUnit.SECONDS)).map(_.toRunSpec)
    }

    override def enforceRoleSetting(id: AbsolutePathId): Boolean = false

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
    runSpecProvider = runSpecProvider,
    taskOpFactory = launcherModule.taskOpFactory,
    localRegion = () => scheduler.getLocalRegion,
    initialFrameworkInfo = initialFrameworkInfo)(actorsModule.materializer, ExecutionContext.global)

  taskJobsModule.expungeOverdueLostTasks(instanceTrackerModule.instanceTracker)

  taskJobsModule.handleOverdueTasks(
    instanceTrackerModule.instanceTracker,
    killService,
    metrics)

  launchQueueModule.reviveOffersActor()
}

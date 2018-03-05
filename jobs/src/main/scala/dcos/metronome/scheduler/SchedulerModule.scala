package dcos.metronome
package scheduler

import java.time.Clock

import akka.actor.{ ActorRefFactory, ActorSystem }
import akka.event.EventStream
import dcos.metronome.repository.SchedulerRepositoriesModule
import dcos.metronome.scheduler.impl.{ NotifyOfTaskStateOperationStep, PeriodicOperationsImpl, ReconciliationActor, SchedulerServiceImpl }
import dcos.metronome.MetricsModule
import mesosphere.marathon._
import mesosphere.marathon.core.base.{ ActorsModule, CrashStrategy, LifecycleState }
import mesosphere.marathon.core.election.{ ElectionModule, ElectionService }
import mesosphere.marathon.core.flow.FlowModule
import mesosphere.marathon.core.instance.update.InstanceChangeHandler
import mesosphere.marathon.core.launcher.{ LauncherModule, OfferProcessor }
import mesosphere.marathon.core.launchqueue.LaunchQueueModule
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.core.matcher.base.OfferMatcher
import mesosphere.marathon.core.matcher.base.util.StopOnFirstMatchingOfferMatcher
import mesosphere.marathon.core.matcher.manager.OfferMatcherManagerModule
import mesosphere.marathon.core.matcher.reconcile.OfferMatcherReconciliationModule
import mesosphere.marathon.core.plugin.PluginModule
import mesosphere.marathon.core.task.jobs.TaskJobsModule
import mesosphere.marathon.core.task.termination.{ KillService, TaskTerminationModule }
import mesosphere.marathon.core.task.tracker._
import mesosphere.marathon.core.task.update.impl.TaskStatusUpdateProcessorImpl
import mesosphere.marathon.core.task.update.impl.steps.ContinueOnErrorStep
import mesosphere.marathon.core.task.update.{ TaskStatusUpdateProcessor }
import mesosphere.marathon.state._
import mesosphere.marathon.storage.repository.{ FrameworkIdRepository, FrameworkIdRepositoryImpl, InstanceRepository }
import mesosphere.util.state._

import scala.collection.immutable.Seq
import scala.util.Random

class SchedulerModule(
  config:            SchedulerConfig,
  actorSystem:       ActorSystem,
  clock:             Clock,
  persistenceModule: SchedulerRepositoriesModule,
  pluginModule:      PluginModule,
  metricsModule:     MetricsModule,
  lifecycleState:    LifecycleState,
  crashStrategy:     CrashStrategy) {

  private[this] lazy val scallopConf: AllConf = config.scallopConf
  private[this] lazy val marathonClock: java.time.Clock = new mesosphere.marathon.core.base.Clock {
    override def now(): Timestamp = Timestamp(clock.instant().toEpochMilli)
  }

  private[this] lazy val metrics = metricsModule.metrics
  private[this] lazy val random = Random
  private[this] lazy val actorsModule = new ActorsModule(actorSystem)

  private[this] lazy val eventBus: EventStream = actorSystem.eventStream

  lazy val schedulerDriverHolder: MarathonSchedulerDriverHolder = new MarathonSchedulerDriverHolder

  private[this] lazy val hostPort: String = config.hostnameWithPort

  private[this] lazy val electionModule: ElectionModule = new ElectionModule(
    scallopConf,
    actorSystem,
    eventBus,
    hostPort,
    lifecycleState,
    crashStrategy)
  lazy val leadershipModule: LeadershipModule = {
    val actorRefFactory: ActorRefFactory = actorsModule.actorRefFactory
    val electionService: ElectionService = electionModule.service

    LeadershipModule(actorRefFactory)
  }
  lazy val instanceTrackerModule: InstanceTrackerModule = {
    val instanceRepository: InstanceRepository = persistenceModule.instanceRepository
    val updateSteps: Seq[InstanceChangeHandler] = Seq(
      ContinueOnErrorStep(new NotifyOfTaskStateOperationStep(eventBus, clock)))

    new InstanceTrackerModule(marathonClock, scallopConf, leadershipModule, instanceRepository, updateSteps)(actorsModule.materializer)
  }

  private[this] lazy val offerMatcherManagerModule = new OfferMatcherManagerModule(
    // infrastructure
    marathonClock, random, scallopConf, actorSystem.scheduler,
    leadershipModule)

  private[this] lazy val offerMatcherReconcilerModule =
    new OfferMatcherReconciliationModule(
      scallopConf,
      marathonClock,
      actorSystem.eventStream,
      instanceTrackerModule.instanceTracker,
      persistenceModule.groupRepository,
      leadershipModule)

  private[this] lazy val launcherModule: LauncherModule = {
    val instanceCreationHandler: InstanceCreationHandler = instanceTrackerModule.instanceCreationHandler
    val offerMatcher: OfferMatcher = StopOnFirstMatchingOfferMatcher(
      offerMatcherReconcilerModule.offerMatcherReconciler,
      offerMatcherManagerModule.globalOfferMatcher)

    new LauncherModule(scallopConf, instanceCreationHandler, schedulerDriverHolder, offerMatcher, pluginModule.pluginManager)
  }

  private[this] lazy val frameworkIdUtil: FrameworkIdRepository = new FrameworkIdRepositoryImpl(
    persistenceModule.frameworkIdStore,
    timeout = config.zkTimeout,
    key = "id")

  private[this] lazy val taskTerminationModule: TaskTerminationModule = new TaskTerminationModule(
    instanceTrackerModule,
    leadershipModule,
    schedulerDriverHolder,
    config.taskKillConfig,
    marathonClock)
  lazy val taskKillService: KillService = taskTerminationModule.taskKillService

  private[this] lazy val scheduler: MarathonScheduler = {
    val taskTracker: InstanceTracker = instanceTrackerModule.instanceTracker
    val stateOpProcessor: TaskStateOpProcessor = instanceTrackerModule.stateOpProcessor
    val offerProcessor: OfferProcessor = launcherModule.offerProcessor
    val taskStatusProcessor: TaskStatusUpdateProcessor = new TaskStatusUpdateProcessorImpl(
      marathonClock, taskTracker, stateOpProcessor, schedulerDriverHolder, taskKillService, eventBus)
    val leaderInfo = config.mesosLeaderUiUrl match {
      case someUrl @ Some(_) => ConstMesosLeaderInfo(someUrl)
      case None              => new MutableMesosLeaderInfo
    }

    new MarathonScheduler(
      eventBus,
      offerProcessor,
      taskStatusProcessor,
      frameworkIdUtil,
      leaderInfo,
      scallopConf)
  }

  private[this] val schedulerDriverFactory: SchedulerDriverFactory = new MesosSchedulerDriverFactory(
    holder = schedulerDriverHolder,
    config = scallopConf,
    httpConfig = scallopConf,
    frameworkIdUtil = frameworkIdUtil,
    scheduler = scheduler)

  private[this] lazy val prePostDriverCallbacks: Seq[PrePostDriverCallback] = Seq(
    persistenceModule.taskStore,
    persistenceModule.frameworkIdStore,
    persistenceModule.groupStore).collect {
      case l: PrePostDriverCallback => l
    }

  private[this] lazy val periodicOperations: PeriodicOperations = new PeriodicOperationsImpl()

  lazy val schedulerService: SchedulerService = new SchedulerServiceImpl(
    leadershipModule.coordinator(),
    config,
    electionModule.service,
    prePostDriverCallbacks,
    schedulerDriverFactory,
    persistenceModule.migration,
    periodicOperations)

  lazy val flowModule = new FlowModule(leadershipModule)
  // make sure launch tokens get initialized
  flowModule.refillOfferMatcherManagerLaunchTokens(
    scallopConf, offerMatcherManagerModule.subOfferMatcherManager)

  lazy val electionService: ElectionService = electionModule.service

  /** Combine offersWanted state from multiple sources. */
  private[this] lazy val offersWanted =
    offerMatcherManagerModule.globalOfferMatcherWantsOffers
      .combineLatest(offerMatcherReconcilerModule.offersWantedObservable)
      .map { case (managerWantsOffers, reconciliationWantsOffers) => managerWantsOffers || reconciliationWantsOffers }

  lazy val launchQueueModule = new LaunchQueueModule(
    scallopConf,
    leadershipModule,
    marathonClock,
    offerMatcherManagerModule.subOfferMatcherManager,
    maybeOfferReviver = flowModule.maybeOfferReviver(marathonClock, scallopConf, eventBus, offersWanted, schedulerDriverHolder),
    taskTracker = instanceTrackerModule.instanceTracker,
    taskOpFactory = launcherModule.taskOpFactory)

  leadershipModule.startWhenLeader(
    props = ReconciliationActor.props(schedulerDriverHolder, instanceTrackerModule.instanceTracker, config),
    name = "reconciliationActor")

  val taskJobsModule = new TaskJobsModule(config.scallopConf, leadershipModule, marathonClock)

  taskJobsModule.expungeOverdueLostTasks(
    instanceTrackerModule.instanceTracker, instanceTrackerModule.stateOpProcessor)

  taskJobsModule.handleOverdueTasks(
    instanceTrackerModule.instanceTracker,
    instanceTrackerModule.stateOpProcessor,
    taskKillService)
}

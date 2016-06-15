package dcos.metronome.scheduler

import akka.actor.{ ActorRefFactory, ActorSystem }
import akka.event.EventStream
import dcos.metronome.repository.SchedulerRepositoriesModule
import dcos.metronome.scheduler.impl.{ PeriodicOperationsImpl, SchedulerCallbacksImpl, SchedulerServiceImpl }
import dcos.metronome.utils.time.Clock
import dcos.metronome.MetricsModule
import mesosphere.marathon._
import mesosphere.marathon.core.base.{ ActorsModule, ShutdownHooks }
import mesosphere.marathon.core.election.{ ElectionModule, ElectionService }
import mesosphere.marathon.core.flow.FlowModule
import mesosphere.marathon.core.launcher.{ LauncherModule, OfferProcessor }
import mesosphere.marathon.core.launchqueue.LaunchQueueModule
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.core.matcher.base.OfferMatcher
import mesosphere.marathon.core.matcher.base.util.StopOnFirstMatchingOfferMatcher
import mesosphere.marathon.core.matcher.manager.OfferMatcherManagerModule
import mesosphere.marathon.core.matcher.reconcile.OfferMatcherReconciliationModule
import mesosphere.marathon.core.plugin.PluginModule
import mesosphere.marathon.core.task.bus.TaskBusModule
import mesosphere.marathon.core.task.tracker._
import mesosphere.marathon.core.task.update.impl.TaskStatusUpdateProcessorImpl
import mesosphere.marathon.core.task.update.impl.steps.{ ContinueOnErrorStep, PostToEventStreamStepImpl }
import mesosphere.marathon.core.task.update.{ TaskStatusUpdateProcessor, TaskUpdateStep }
import mesosphere.marathon.event.EventModule
import mesosphere.marathon.state._
import mesosphere.util.state._

import scala.collection.immutable.Seq
import scala.util.Random

class SchedulerModule(
    config:            SchedulerConfig,
    actorSystem:       ActorSystem,
    clock:             Clock,
    persistenceModule: SchedulerRepositoriesModule
) {

  private[this] lazy val scallopConf: AllConf = config.scallopConf
  private[this] lazy val marathonClock = new mesosphere.marathon.core.base.Clock {
    override def now(): Timestamp = Timestamp(clock.now())
  }

  lazy val metricsModule = new MetricsModule()
  private[this] lazy val metrics = metricsModule.metrics
  private[this] lazy val random = Random
  private[this] lazy val shutdownHooks = ShutdownHooks()
  private[this] lazy val actorsModule = new ActorsModule(shutdownHooks, actorSystem)

  private[this] lazy val eventModule: EventModule = new EventModule(scallopConf)
  private[this] lazy val eventBus: EventStream = eventModule.provideEventBus(actorSystem)

  lazy val schedulerDriverHolder: MarathonSchedulerDriverHolder = new MarathonSchedulerDriverHolder

  private[this] lazy val hostPort: String = {
    val port = if (config.disableHttp) config.httpsPort else config.httpPort
    "%s:%d".format(config.hostname, port)
  }
  private[this] lazy val electionModule: ElectionModule = new ElectionModule(
    scallopConf,
    actorSystem,
    eventBus,
    scallopConf,
    metrics,
    hostPort,
    shutdownHooks
  )
  private[this] lazy val leadershipModule: LeadershipModule = {
    val actorRefFactory: ActorRefFactory = actorsModule.actorRefFactory
    val electionService: ElectionService = electionModule.service

    LeadershipModule(actorRefFactory, electionService)
  }
  private[this] lazy val taskTrackerModule: TaskTrackerModule = {
    val taskRepository: TaskRepository = persistenceModule.taskRepository
    val updateSteps: Seq[TaskUpdateStep] = Seq(
      // TODO: write a custom TaskUpdateStep that provides a model that we expect
      ContinueOnErrorStep(new PostToEventStreamStepImpl(eventBus))
    )

    new TaskTrackerModule(marathonClock, metrics, scallopConf, leadershipModule, taskRepository, updateSteps)
  }

  private[this] lazy val offerMatcherManagerModule = new OfferMatcherManagerModule(
    // infrastructure
    marathonClock, random, metrics, scallopConf,
    leadershipModule
  )

  private[this] lazy val offerMatcherReconcilerModule =
    new OfferMatcherReconciliationModule(
      scallopConf,
      marathonClock,
      actorSystem.eventStream,
      taskTrackerModule.taskTracker,
      persistenceModule.groupRepository,
      offerMatcherManagerModule.subOfferMatcherManager,
      leadershipModule
    )

  // TODO: JobsPlugins vs MarathonPlugins?!
  private[this] lazy val pluginModule = new PluginModule(scallopConf)
  private[this] lazy val launcherModule: LauncherModule = {
    val taskCreationHandler: TaskCreationHandler = taskTrackerModule.taskCreationHandler
    val offerMatcher: OfferMatcher = StopOnFirstMatchingOfferMatcher(
      offerMatcherReconcilerModule.offerMatcherReconciler,
      offerMatcherManagerModule.globalOfferMatcher
    )

    new LauncherModule(
      marathonClock, metrics, scallopConf, taskCreationHandler, schedulerDriverHolder, offerMatcher, pluginModule.pluginManager
    )
  }

  private[this] lazy val frameworkIdUtil: FrameworkIdUtil = new FrameworkIdUtil(
    persistenceModule.frameworkIdStore,
    timeout = config.zkTimeoutDuration,
    key = "id"
  )
  private[this] lazy val scheduler: MarathonScheduler = {
    val taskTracker: TaskTracker = taskTrackerModule.taskTracker
    val stateOpProcessor: TaskStateOpProcessor = taskTrackerModule.stateOpProcessor
    val offerProcessor: OfferProcessor = launcherModule.offerProcessor
    val taskStatusProcessor: TaskStatusUpdateProcessor = new TaskStatusUpdateProcessorImpl(
      metrics, marathonClock, taskTracker, stateOpProcessor, schedulerDriverHolder
    )
    val leaderInfo = config.mesosLeaderUiUrl match {
      case someUrl @ Some(_) => ConstMesosLeaderInfo(someUrl)
      case None              => new MutableMesosLeaderInfo
    }
    val schedulerCallbacks: SchedulerCallbacks = new SchedulerCallbacksImpl(electionModule.service)

    new MarathonScheduler(
      eventBus,
      marathonClock,
      offerProcessor,
      taskStatusProcessor,
      frameworkIdUtil,
      leaderInfo,
      actorSystem,
      scallopConf,
      schedulerCallbacks
    )
  }

  private[this] val schedulerDriverFactory: SchedulerDriverFactory = new MesosSchedulerDriverFactory(
    holder = schedulerDriverHolder,
    config = scallopConf,
    httpConfig = scallopConf,
    frameworkIdUtil = frameworkIdUtil,
    scheduler = scheduler
  )

  private[this] lazy val prePostDriverCallbacks: Seq[PrePostDriverCallback] = Seq(
    persistenceModule.taskStore,
    persistenceModule.frameworkIdStore,
    persistenceModule.groupStore
  ).collect {
    case l: PrePostDriverCallback => l
  }

  private[this] lazy val periodicOperations: PeriodicOperations = new PeriodicOperationsImpl()

  lazy val schedulerService: SchedulerService = new SchedulerServiceImpl(
    leadershipModule.coordinator(),
    scallopConf,
    electionModule.service,
    prePostDriverCallbacks,
    schedulerDriverFactory,
    metrics,
    persistenceModule.migration,
    periodicOperations
  )

  lazy val taskBusModule = new TaskBusModule()
  lazy val flowModule = new FlowModule(leadershipModule)
  // make sure launch tokens get initialized
  flowModule.refillOfferMatcherManagerLaunchTokens(
    scallopConf, taskBusModule.taskStatusObservables, offerMatcherManagerModule.subOfferMatcherManager
  )

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
    taskTracker = taskTrackerModule.taskTracker,
    taskOpFactory = launcherModule.taskOpFactory
  )

}

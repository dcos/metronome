package dcos.metronome.scheduler

import akka.actor.{ ActorRefFactory, ActorSystem }
import akka.event.EventStream
import dcos.metronome.persistence.PersistenceModule
import dcos.metronome.scheduler.impl.{ PeriodicOperationsImpl, SchedulerCallbacksImpl, SchedulerServiceImpl }
import dcos.metronome.utils.time.Clock
import dcos.metronome.{ JobsConfig, MetricsModule }
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
import mesosphere.marathon.core.task.update.{ TaskStatusUpdateProcessor, TaskUpdateStep }
import mesosphere.marathon.event.EventModule
import mesosphere.marathon.state._
import mesosphere.util.state._
import org.slf4j.LoggerFactory

import scala.collection.immutable.Seq
import scala.util.Random

class SchedulerModule(config: JobsConfig, actorSystem: ActorSystem, clock: Clock) {

  private[this] lazy val marathonClock = new mesosphere.marathon.core.base.Clock {
    override def now(): Timestamp = Timestamp(clock.now())
  }
  private[this] val log = LoggerFactory.getLogger(getClass)

  private[this] val conf = config.scallopConf

  private[this] lazy val metrics = new MetricsModule().metrics
  private[this] lazy val random = Random
  private[this] lazy val shutdownHooks = ShutdownHooks()
  private[this] lazy val actorsModule = new ActorsModule(shutdownHooks, actorSystem)

  private[this] lazy val persistenceModule = new PersistenceModule(conf, metrics)
  private[this] lazy val eventModule: EventModule = new EventModule(conf)
  private[this] lazy val eventBus: EventStream = eventModule.provideEventBus(actorSystem)

  private[this] lazy val schedulerDriverHolder: MarathonSchedulerDriverHolder = new MarathonSchedulerDriverHolder

  private[this] lazy val hostPort: String = {
    val port = if (conf.disableHttp()) conf.httpsPort() else conf.httpPort()
    "%s:%d".format(conf.hostname(), port)
  }
  private[this] lazy val electionModule: ElectionModule = new ElectionModule(
    conf,
    actorSystem,
    eventBus,
    conf,
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
    // FIXME (wiring): NotifyJobRunServiceStep - requires ref to JobRunService
    val updateSteps: Seq[TaskUpdateStep] = Seq.empty[TaskUpdateStep]

    new TaskTrackerModule(marathonClock, metrics, conf, leadershipModule, taskRepository, updateSteps)
  }

  private[this] lazy val offerMatcherManagerModule = new OfferMatcherManagerModule(
    // infrastructure
    marathonClock, random, metrics, conf,
    leadershipModule
  )

  private[this] lazy val offerMatcherReconcilerModule =
    new OfferMatcherReconciliationModule(
      conf,
      marathonClock,
      actorSystem.eventStream,
      taskTrackerModule.taskTracker,
      persistenceModule.groupRepository,
      offerMatcherManagerModule.subOfferMatcherManager,
      leadershipModule
    )

  private[this] lazy val pluginModule = new PluginModule(conf)
  private[this] lazy val launcherModule: LauncherModule = {
    val taskCreationHandler: TaskCreationHandler = taskTrackerModule.taskCreationHandler
    val offerMatcher: OfferMatcher = StopOnFirstMatchingOfferMatcher(
      offerMatcherReconcilerModule.offerMatcherReconciler,
      offerMatcherManagerModule.globalOfferMatcher
    )

    new LauncherModule(
      marathonClock, metrics, conf, taskCreationHandler, schedulerDriverHolder, offerMatcher, pluginModule.pluginManager
    )
  }

  private[this] lazy val frameworkIdUtil: FrameworkIdUtil = new FrameworkIdUtil(
    persistenceModule.frameworkIdStore,
    timeout = conf.zkTimeoutDuration,
    key = "id"
  )
  private[this] lazy val scheduler: MarathonScheduler = {
    val taskTracker: TaskTracker = taskTrackerModule.taskTracker
    val stateOpProcessor: TaskStateOpProcessor = taskTrackerModule.stateOpProcessor
    val offerProcessor: OfferProcessor = launcherModule.offerProcessor
    val taskStatusProcessor: TaskStatusUpdateProcessor = new TaskStatusUpdateProcessorImpl(
      metrics, marathonClock, taskTracker, stateOpProcessor, schedulerDriverHolder
    )
    val leaderInfo = conf.mesosLeaderUiUrl.get match {
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
      conf,
      schedulerCallbacks
    )
  }

  private[this] val schedulerDriverFactory: SchedulerDriverFactory = new MesosSchedulerDriverFactory(
    holder = schedulerDriverHolder,
    config = conf,
    httpConfig = conf,
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
    conf,
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
    conf, taskBusModule.taskStatusObservables, offerMatcherManagerModule.subOfferMatcherManager
  )

  /** Combine offersWanted state from multiple sources. */
  private[this] lazy val offersWanted =
    offerMatcherManagerModule.globalOfferMatcherWantsOffers
      .combineLatest(offerMatcherReconcilerModule.offersWantedObservable)
      .map { case (managerWantsOffers, reconciliationWantsOffers) => managerWantsOffers || reconciliationWantsOffers }

  lazy val launchQueueModule = new LaunchQueueModule(
    conf,
    leadershipModule,
    marathonClock,
    offerMatcherManagerModule.subOfferMatcherManager,
    maybeOfferReviver = flowModule.maybeOfferReviver(marathonClock, conf, eventBus, offersWanted, schedulerDriverHolder),
    taskTracker = taskTrackerModule.taskTracker,
    taskOpFactory = launcherModule.taskOpFactory
  )

}

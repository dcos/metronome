package dcos.metronome
package scheduler

import java.time.Clock
import java.util.concurrent.Executors

import akka.actor.{ ActorRefFactory, ActorSystem }
import akka.event.EventStream
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Sink, Source }
import dcos.metronome.repository.SchedulerRepositoriesModule
import dcos.metronome.scheduler.impl.{ NotifyOfTaskStateOperationStep, PeriodicOperationsImpl, ReconciliationActor, SchedulerServiceImpl }
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
import mesosphere.marathon.core.task.update.TaskStatusUpdateProcessor
import mesosphere.marathon.core.task.update.impl.TaskStatusUpdateProcessorImpl
import mesosphere.marathon.core.task.update.impl.steps.ContinueOnErrorStep
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.storage.repository.InstanceRepository
import mesosphere.util.state._
import org.apache.mesos.Protos.Offer

import scala.concurrent.ExecutionContext
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

  private[this] lazy val electionModule: ElectionModule = new ElectionModule(
    metrics,
    scallopConf,
    actorSystem,
    eventBus,
    hostPort,
    crashStrategy,
    ExecutionContext.fromExecutor(electionExecutor))
  lazy val leadershipModule: LeadershipModule = {
    val actorRefFactory: ActorRefFactory = actorsModule.actorRefFactory

    LeadershipModule(actorRefFactory)
  }
  lazy val instanceTrackerModule: InstanceTrackerModule = {
    val instanceRepository: InstanceRepository = persistenceModule.instanceRepository
    val updateSteps: Seq[InstanceChangeHandler] = Seq(
      ContinueOnErrorStep(new NotifyOfTaskStateOperationStep(eventBus, clock)))

    new InstanceTrackerModule(metrics, clock, scallopConf, leadershipModule, instanceRepository, updateSteps)(actorsModule.materializer)
  }

  private[this] lazy val offerMatcherManagerModule = new OfferMatcherManagerModule(
    // infrastructure
    metrics, clock, random, scallopConf,
    leadershipModule,
    () => scheduler.getLocalRegion)(actorsModule.materializer)

  private[this] lazy val offerMatcherReconcilerModule =
    new OfferMatcherReconciliationModule(
      scallopConf,
      clock,
      actorSystem.eventStream,
      instanceTrackerModule.instanceTracker,
      persistenceModule.groupRepository,
      leadershipModule)(actorsModule.materializer)

  lazy val taskStatusProcessor: TaskStatusUpdateProcessor = new TaskStatusUpdateProcessorImpl(
    metrics, clock, instanceTrackerModule.instanceTracker, schedulerDriverHolder, killService, eventBus)

  private[this] lazy val launcherModule: LauncherModule = {
    val instanceTracker: InstanceTracker = instanceTrackerModule.instanceTracker
    val offerMatcher: OfferMatcher = StopOnFirstMatchingOfferMatcher(
      offerMatcherReconcilerModule.offerMatcherReconciler,
      offerMatcherManagerModule.globalOfferMatcher)

    new LauncherModule(metrics, scallopConf, instanceTracker, schedulerDriverHolder, offerMatcher, pluginModule.pluginManager)(clock)
  }

  private[this] lazy val taskTerminationModule: TaskTerminationModule = new TaskTerminationModule(
    instanceTrackerModule,
    leadershipModule,
    schedulerDriverHolder,
    config.taskKillConfig,
    clock)
  lazy val killService: KillService = taskTerminationModule.taskKillService

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
      scallopConf)
  }

  private[this] val schedulerDriverFactory: SchedulerDriverFactory = new MesosSchedulerDriverFactory(
    holder = schedulerDriverHolder,
    config = scallopConf,
    httpConfig = scallopConf,
    frameworkIdRepository = persistenceModule.frameworkIdRepository,
    scheduler = scheduler)

  private[this] lazy val prePostDriverCallbacks: Seq[PrePostDriverCallback] = Seq(
    persistenceModule.instanceRepository,
    persistenceModule.frameworkIdRepository,
    persistenceModule.groupRepository).collect {
      case l: PrePostDriverCallback => l
    }

  private[this] lazy val periodicOperations: PeriodicOperations = new PeriodicOperationsImpl()

  lazy val schedulerService: SchedulerService = new SchedulerServiceImpl(
    persistenceModule.storageModule.persistenceStore,
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
    clock,
    offerMatcherManagerModule.subOfferMatcherManager,
    maybeOfferReviver = flowModule.maybeOfferReviver(metrics, clock, scallopConf, eventBus, offersWanted, schedulerDriverHolder),
    taskTracker = instanceTrackerModule.instanceTracker,
    taskOpFactory = launcherModule.taskOpFactory,
    () => scheduler.getLocalRegion)

  leadershipModule.startWhenLeader(
    props = ReconciliationActor.props(schedulerDriverHolder, instanceTrackerModule.instanceTracker, config),
    name = "reconciliationActor")

  val taskJobsModule = new TaskJobsModule(config.scallopConf, leadershipModule, clock)

  taskJobsModule.expungeOverdueLostTasks(instanceTrackerModule.instanceTracker)

  taskJobsModule.handleOverdueTasks(
    instanceTrackerModule.instanceTracker,
    killService)
}

package dcos.metronome.history.impl

import akka.actor.{ ActorLogging, ActorRef, Props, Actor }
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.history.JobHistoryConfig
import dcos.metronome.model.{ RunInfo, JobRun, Event, JobHistory }
import dcos.metronome.repository.{ Repository, LoadContentOnStartup }
import dcos.metronome.utils.time.Clock
import mesosphere.marathon.state.PathId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise

class JobHistoryServiceActor(config: JobHistoryConfig, clock: Clock, val repo: Repository[PathId, JobHistory], val behavior: Behavior)
    extends Actor with ActorLogging with LoadContentOnStartup[PathId, JobHistory] with ActorBehavior {
  import JobHistoryServiceActor._
  import JobHistoryPersistenceActor._

  val statusMap = TrieMap.empty[PathId, JobHistory].withDefault(JobHistory.empty)
  var persistenceActor: ActorRef = _

  override def preStart(): Unit = {
    super.preStart()
    persistenceActor = context.actorOf(JobHistoryPersistenceActor.props(repo, behavior))
    context.system.eventStream.subscribe(self, classOf[Event.JobRunEvent])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  override def receive: Receive = around {
    //event stream events
    case Event.JobRunStarted(run, _, _)  => started(run)
    case Event.JobRunFinished(run, _, _) => finished(run)
    case Event.JobRunFailed(run, _, _)   => failed(run)
    case Event.JobRunUpdate(run, _, _)   => //ignore

    //service events
    case GetJobStatus(id, promise)       => promise.success(statusMap.get(id))
    case ListJobStatus(filter, promise)  => promise.success(statusMap.values.filter(filter))

    //persistence events
    case JobHistoryCreated(_, status, _) => statusMap += status.id -> status
    case JobHistoryUpdated(_, status, _) => statusMap += status.id -> status
    case JobHistoryDeleted(_, status, _) => statusMap -= status.id
    case PersistFailed(_, id, ex, _)     => log.error(ex, s"Could not persist JobStatus for $id")
  }

  def started(run: JobRun): Unit = {
    log.debug(s"JobRun: ${run.id} has been reported started.")
    val id = run.jobSpec.id
    if (!statusMap.contains(id)) persistenceActor ! Create(id, JobHistory.empty(id))
  }

  def finished(run: JobRun): Unit = {
    log.debug(s"JobRun: ${run.id} has been reported finished successfully.")
    def update(status: JobHistory): JobHistory = status.copy(
      successCount = status.successCount + 1,
      lastSuccessAt = Some(clock.now()),
      successfulFinishedRuns = runHistory(run, status.successfulFinishedRuns)
    )
    persistenceActor ! Update(run.jobSpec.id, update)
  }

  def failed(run: JobRun): Unit = {
    log.debug(s"JobRun: ${run.id} has been reported failed.")
    def update(status: JobHistory): JobHistory = status.copy(
      failureCount = status.failureCount + 1,
      lastFailureAt = Some(clock.now()),
      failedFinishedRuns = runHistory(run, status.failedFinishedRuns)
    )
    persistenceActor ! Update(run.jobSpec.id, update)
  }

  def runHistory(run: JobRun, seq: Seq[RunInfo]): Seq[RunInfo] = {
    (RunInfo(run) +: seq).take(config.runHistoryCount)
  }

  override def initialize(all: List[JobHistory]): Unit = {
    all.foreach(a => statusMap += a.id -> a)
  }
}

object JobHistoryServiceActor {
  case class GetJobStatus(id: PathId, promise: Promise[Option[JobHistory]])
  case class ListJobStatus(filter: JobHistory => Boolean, promise: Promise[Iterable[JobHistory]])

  def props(config: JobHistoryConfig, clock: Clock, repo: Repository[PathId, JobHistory], behavior: Behavior): Props = {
    Props(new JobHistoryServiceActor(config, clock, repo, behavior))
  }
}

package dcos.metronome
package history.impl

import java.time.Clock

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.history.JobHistoryConfig
import dcos.metronome.model._
import dcos.metronome.repository.{ LoadContentOnStartup, Repository }

import scala.collection.concurrent.TrieMap

class JobHistoryServiceActor(config: JobHistoryConfig, clock: Clock, val repo: Repository[JobId, JobHistory], val behavior: Behavior)
    extends Actor with ActorLogging with LoadContentOnStartup[JobId, JobHistory] with ActorBehavior {
  import JobHistoryServiceActor._
  import JobHistoryPersistenceActor._

  val jobHistoryMap = TrieMap.empty[JobId, JobHistory].withDefault(JobHistory.empty)
  var persistenceActor: ActorRef = _

  override def preStart(): Unit = {
    super.preStart()
    persistenceActor = context.actorOf(JobHistoryPersistenceActor.props(repo, behavior))
    context.system.eventStream.subscribe(self, classOf[Event.JobRunEvent])
    context.system.eventStream.subscribe(self, classOf[Event.JobSpecDeleted])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  override def receive: Receive = around {
    //event stream events
    case Event.JobRunStarted(run, _, _)      => started(run)
    case Event.JobRunFinished(run, _, _)     => finished(run)
    case Event.JobRunFailed(run, _, _)       => failed(run)
    case Event.JobRunUpdate(run, _, _)       => //ignore
    case Event.JobSpecDeleted(spec, _, _)    => deleteHistoryFor(spec)

    //service events
    case GetJobHistory(id)                   => sender() ! jobHistoryMap.get(id)
    case ListJobHistories(filter)            => sender() ! jobHistoryMap.values.filter(filter)

    //persistence events
    case JobHistoryCreated(_, jobHistory, _) => jobHistoryMap += jobHistory.jobSpecId -> jobHistory
    case JobHistoryUpdated(_, jobHistory, _) => jobHistoryMap += jobHistory.jobSpecId -> jobHistory
    case JobHistoryDeleted(_, jobHistory, _) => jobHistoryMap -= jobHistory.jobSpecId
    case PersistFailed(_, id, ex, _)         => log.error(ex, s"Could not persist JobHistory for $id")
  }

  def started(run: JobRun): Unit = {
    log.debug(s"JobRun: ${run.id} has been reported started.")
    val id = run.id.jobId
    if (!jobHistoryMap.contains(id)) persistenceActor ! Create(id, JobHistory.empty(id))
  }

  def finished(run: JobRun): Unit = {
    log.debug(s"JobRun: ${run.id} has been reported finished successfully.")
    def update(jobHistory: JobHistory): JobHistory = jobHistory.copy(
      successCount = jobHistory.successCount + 1,
      lastSuccessAt = Some(clock.instant()),
      successfulRuns = runHistory(run, jobHistory.successfulRuns))
    persistenceActor ! Update(run.id.jobId, update)
  }

  def failed(run: JobRun): Unit = {
    log.debug(s"JobRun: ${run.id} has been reported failed.")
    def update(jobHistory: JobHistory): JobHistory = jobHistory.copy(
      failureCount = jobHistory.failureCount + 1,
      lastFailureAt = Some(clock.instant()),
      failedRuns = runHistory(run, jobHistory.failedRuns))
    persistenceActor ! Update(run.id.jobId, update)
  }

  def deleteHistoryFor(jobSpec: JobSpec): Unit = {
    log.debug(s"Delete history for: ${jobSpec.id} since the job spec has been deleted.")
    persistenceActor ! Delete(jobSpec.id, jobHistoryMap(jobSpec.id))
  }

  def runHistory(run: JobRun, seq: Seq[JobRunInfo]): Seq[JobRunInfo] = {
    (JobRunInfo(run) +: seq).take(config.runHistoryCount)
  }

  override def initialize(all: List[JobHistory]): Unit = {
    all.foreach(a => jobHistoryMap += a.jobSpecId -> a)
  }
}

object JobHistoryServiceActor {
  case class GetJobHistory(id: JobId)
  case class ListJobHistories(filter: JobHistory => Boolean)

  def props(config: JobHistoryConfig, clock: Clock, repo: Repository[JobId, JobHistory], behavior: Behavior): Props = {
    Props(new JobHistoryServiceActor(config, clock, repo, behavior))
  }
}

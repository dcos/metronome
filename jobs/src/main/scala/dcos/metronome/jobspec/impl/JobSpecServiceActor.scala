package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.behavior.{ ActorBehavior, Behavior }
import dcos.metronome.model.{ Event, JobSpec }
import dcos.metronome.repository.{ LoadContentOnStartup, Repository }
import dcos.metronome.{ JobSpecAlreadyExists, JobSpecChangeInFlight, JobSpecDoesNotExist }
import mesosphere.marathon.state.PathId

import scala.collection.concurrent.TrieMap

/**
  * This actor knows all available JobSpecs (as view) and manages all available JobSpecExecutors.
  */
//noinspection AccessorLikeMethodIsUnit
class JobSpecServiceActor(
    val repo:                Repository[PathId, JobSpec],
    persistenceActorFactory: PathId => Props,
    schedulerActorFactory:   JobSpec => Props,
    val behavior:            Behavior
) extends LoadContentOnStartup[PathId, JobSpec] with ActorBehavior {
  import JobSpecPersistenceActor._
  import JobSpecServiceActor._

  private[impl] val allJobs = TrieMap.empty[PathId, JobSpec]
  private[impl] var inFlightChanges = Set.empty[PathId]
  private[impl] val scheduleActors = TrieMap.empty[PathId, ActorRef]
  private[impl] val persistenceActors = TrieMap.empty[PathId, ActorRef]

  override def receive: Receive = around {
    // crud messages
    case CreateJobSpec(jobSpec)             => createJobSpec(jobSpec)
    case UpdateJobSpec(id, change)          => updateJobSpec(id, change)
    case DeleteJobSpec(id)                  => deleteJobSpec(id)
    case GetJobSpec(id)                     => getJobSpec(id)
    case ListJobSpecs(filter)               => listJobSpecs(filter)

    // persistence ack messages
    case Created(_, jobSpec, delegate)      => jobSpecCreated(jobSpec, delegate)
    case Updated(_, jobSpec, delegate)      => jobSpecUpdated(jobSpec, delegate)
    case Deleted(_, jobSpec, delegate)      => jobSpecDeleted(jobSpec, delegate)
    case PersistFailed(_, id, ex, delegate) => jobChangeFailed(id, ex, delegate)

    // lifetime messages
    case Terminated(ref)                    => handleTerminated(ref)
  }

  def getJobSpec(id: PathId): Unit = {
    sender() ! allJobs.get(id)
  }

  def listJobSpecs(filter: JobSpec => Boolean): Unit = {
    sender() ! allJobs.values.filter(filter)
  }

  def createJobSpec(jobSpec: JobSpec): Unit = {
    noSpecWithId(jobSpec) {
      noChangeInFlight(jobSpec) {
        persistenceActor(jobSpec.id) ! JobSpecPersistenceActor.Create(jobSpec, sender())
      }
    }
  }

  def updateJobSpec(id: PathId, change: JobSpec => JobSpec): Unit = {
    withJob(id) { old =>
      noChangeInFlight(old) {
        persistenceActor(id) ! JobSpecPersistenceActor.Update(change, sender())
      }
    }
  }

  def deleteJobSpec(id: PathId): Unit = {
    withJob(id) { old =>
      noChangeInFlight(old) {
        persistenceActor(id) ! JobSpecPersistenceActor.Delete(old, sender())
      }
    }
  }

  def withJob[T](id: PathId)(fn: (JobSpec) => T): Option[T] = {
    val result = allJobs.get(id).map(fn)
    if (result.isEmpty) sender() ! Status.Failure(JobSpecDoesNotExist(id))
    result
  }

  def noSpecWithId(jobSpec: JobSpec)(change: => Unit): Unit = {
    if (allJobs.contains(jobSpec.id)) sender() ! Status.Failure(JobSpecAlreadyExists(jobSpec.id)) else {
      change
    }
  }

  def noChangeInFlight[T](jobSpec: JobSpec)(change: => Unit): Unit = {
    if (inFlightChanges.contains(jobSpec.id)) sender() ! Status.Failure(JobSpecChangeInFlight(jobSpec.id)) else {
      inFlightChanges += jobSpec.id
      change
    }
  }

  def addJobSpec(jobSpec: JobSpec): Unit = {
    allJobs += jobSpec.id -> jobSpec
    inFlightChanges -= jobSpec.id
    scheduleActor(jobSpec)
  }

  def jobSpecCreated(jobSpec: JobSpec, delegate: ActorRef): Unit = {
    addJobSpec(jobSpec)
    context.system.eventStream.publish(Event.JobSpecCreated(jobSpec))
    delegate ! jobSpec
  }

  def jobSpecUpdated(jobSpec: JobSpec, delegate: ActorRef): Unit = {
    allJobs += jobSpec.id -> jobSpec
    inFlightChanges -= jobSpec.id
    scheduleActor(jobSpec).foreach { scheduler =>
      //TODO: create actors for every schedule, not only head option
      jobSpec.schedules.headOption match {
        case Some(schedule) if schedule.enabled => scheduler ! JobSpecSchedulerActor.UpdateJobSpec(jobSpec)
        case _ =>
          //the updated spec does not have an enabled schedule
          context.unwatch(scheduler)
          context.stop(scheduler)
          scheduleActors -= jobSpec.id
      }
    }
    context.system.eventStream.publish(Event.JobSpecUpdated(jobSpec))
    delegate ! jobSpec
  }

  def jobSpecDeleted(jobSpec: JobSpec, delegate: ActorRef): Unit = {
    def removeFrom(map: TrieMap[PathId, ActorRef]) = map.remove(jobSpec.id).foreach { actorRef =>
      context.unwatch(actorRef)
      context.stop(actorRef)
    }
    allJobs -= jobSpec.id
    inFlightChanges -= jobSpec.id
    removeFrom(persistenceActors)
    removeFrom(scheduleActors)
    context.system.eventStream.publish(Event.JobSpecDeleted(jobSpec))
    delegate ! jobSpec
  }

  def jobChangeFailed(id: PathId, ex: Throwable, delegate: ActorRef): Unit = {
    inFlightChanges -= id
    delegate ! Status.Failure(ex)
  }

  def handleTerminated(ref: ActorRef): Unit = {
    log.error(s"Actor has terminated: $ref")
    //TODO: restart?
  }

  def persistenceActor(id: PathId): ActorRef = {
    def newActor: ActorRef = {
      val ref = context.actorOf(persistenceActorFactory(id), s"persistence:${id.safePath}")
      context.watch(ref)
      persistenceActors += id -> ref
      ref
    }
    persistenceActors.getOrElse(id, newActor)
  }

  def scheduleActor(jobSpec: JobSpec): Option[ActorRef] = {
    //TODO: create actors for every schedule, not only head option
    def newActor: Option[ActorRef] = jobSpec.schedules.headOption.map { schedule =>
      val ref = context.actorOf(schedulerActorFactory(jobSpec), s"scheduler:${jobSpec.id.safePath}")
      context.watch(ref)
      scheduleActors += jobSpec.id -> ref
      ref
    }
    scheduleActors.get(jobSpec.id) orElse newActor
  }

  def initialize(jobs: List[JobSpec]): Unit = {
    log.info(s"Loaded JobSpecs: $jobs")
    jobs.foreach(addJobSpec)
  }
}

object JobSpecServiceActor {

  //crud messages
  case class ListJobSpecs(filter: JobSpec => Boolean)
  case class GetJobSpec(id: PathId)
  case class CreateJobSpec(jobSpec: JobSpec)
  case class UpdateJobSpec(id: PathId, change: JobSpec => JobSpec)
  case class DeleteJobSpec(id: PathId)

  def props(
    repo:                    Repository[PathId, JobSpec],
    persistenceActorFactory: PathId => Props,
    schedulerActorFactory:   JobSpec => Props,
    behavior:                Behavior
  ): Props = Props(new JobSpecServiceActor(repo, persistenceActorFactory, schedulerActorFactory, behavior))
}

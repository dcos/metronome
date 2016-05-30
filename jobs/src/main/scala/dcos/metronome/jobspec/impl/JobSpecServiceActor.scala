package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.model.JobSpec
import dcos.metronome.repository.Repository
import dcos.metronome.{ JobSpecAlreadyExists, JobSpecChangeInFlight, JobSpecDoesNotExist }
import mesosphere.marathon.state.PathId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise

/**
  * This actor knows all available JobSpecs (as view) and manages all available JobSpecExecutors.
  */
//noinspection AccessorLikeMethodIsUnit
class JobSpecServiceActor(
    val repo:                Repository[PathId, JobSpec],
    persistenceActorFactory: PathId => Props,
    schedulerActorFactory:   JobSpec => Props
) extends LoadContentOnStartup[PathId, JobSpec] {
  import JobSpecPersistenceActor._
  import JobSpecServiceActor._

  private[impl] val allJobs = TrieMap.empty[PathId, JobSpec]
  private[impl] var inFlightChanges = Set.empty[PathId]
  private[impl] val scheduleActors = TrieMap.empty[PathId, ActorRef]
  private[impl] val persistenceActors = TrieMap.empty[PathId, ActorRef]

  override def receive: Receive = {
    // crud messages
    case CreateJobSpec(jobSpec, promise)    => createJobSpec(jobSpec, promise)
    case UpdateJobSpec(id, change, promise) => updateJobSpec(id, change, promise)
    case DeleteJobSpec(id, promise)         => deleteJobSpec(id, promise)
    case GetJobSpec(id, promise)            => getJobSpec(id, promise)
    case ListJobSpecs(filter, promise)      => listJobSpecs(filter, promise)

    // persistence ack messages
    case Created(_, jobSpec, promise)       => jobSpecCreated(jobSpec, promise)
    case Updated(_, jobSpec, promise)       => jobSpecUpdated(jobSpec, promise)
    case Deleted(_, jobSpec, promise)       => jobSpecDeleted(jobSpec, promise)
    case PersistFailed(_, id, ex, promise)  => jobChangeFailed(id, ex, promise)

    // lifetime messages
    case Terminated(ref)                    => handleTerminated(ref)
  }

  def getJobSpec(id: PathId, promise: Promise[Option[JobSpec]]): Unit = {
    promise.success(allJobs.get(id))
  }

  def listJobSpecs(filter: JobSpec => Boolean, promise: Promise[Iterable[JobSpec]]): Unit = {
    promise.success(allJobs.values.filter(filter))
  }

  def createJobSpec(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    noSpecWithId(jobSpec, promise) {
      noChangeInFlight(jobSpec, promise) {
        persistenceActor(jobSpec.id) ! JobSpecPersistenceActor.Create(jobSpec, promise)
      }
    }
  }

  def updateJobSpec(id: PathId, change: JobSpec => JobSpec, promise: Promise[JobSpec]): Unit = {
    withJob(id, promise) { old =>
      noChangeInFlight(old, promise) {
        persistenceActor(id) ! JobSpecPersistenceActor.Update(change, promise)
      }
    }
  }

  def deleteJobSpec(id: PathId, promise: Promise[JobSpec]): Unit = {
    withJob(id, promise) { old =>
      noChangeInFlight(old, promise) {
        persistenceActor(id) ! JobSpecPersistenceActor.Delete(old, promise)
      }
    }
  }

  def withJob[T](id: PathId, promise: Promise[JobSpec])(fn: (JobSpec) => T): Option[T] = {
    val result = allJobs.get(id).map(fn)
    if (result.isEmpty) promise.failure(JobSpecDoesNotExist(id))
    result
  }

  def noSpecWithId(jobSpec: JobSpec, promise: Promise[JobSpec])(change: => Unit): Unit = {
    if (allJobs.contains(jobSpec.id)) promise.failure(JobSpecAlreadyExists(jobSpec.id)) else {
      change
    }
  }

  def noChangeInFlight[T](jobSpec: JobSpec, promise: Promise[T])(change: => Unit): Unit = {
    if (inFlightChanges.contains(jobSpec.id)) promise.failure(JobSpecChangeInFlight(jobSpec.id)) else {
      inFlightChanges += jobSpec.id
      change
    }
  }

  def addJobSpec(jobSpec: JobSpec): Unit = {
    allJobs += jobSpec.id -> jobSpec
    inFlightChanges -= jobSpec.id
    scheduleActor(jobSpec)
  }

  def jobSpecCreated(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    addJobSpec(jobSpec)
    promise.success(jobSpec)
  }

  def jobSpecUpdated(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    allJobs += jobSpec.id -> jobSpec
    inFlightChanges -= jobSpec.id
    scheduleActor(jobSpec).foreach { scheduler =>
      jobSpec.schedule match {
        case Some(schedule) if schedule.enabled => scheduler ! JobSpecSchedulerActor.UpdateJobSpec(jobSpec)
        case _ =>
          //the updated spec does not have an enabled schedule
          context.unwatch(scheduler)
          context.stop(scheduler)
          scheduleActors -= jobSpec.id
      }
    }
    promise.success(jobSpec)
  }

  def jobSpecDeleted(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    def removeFrom(map: TrieMap[PathId, ActorRef]) = map.remove(jobSpec.id).foreach { actorRef =>
      context.unwatch(actorRef)
      context.stop(actorRef)
    }
    allJobs -= jobSpec.id
    inFlightChanges -= jobSpec.id
    removeFrom(persistenceActors)
    removeFrom(scheduleActors)
    promise.success(jobSpec)
  }

  def jobChangeFailed(id: PathId, ex: Throwable, promise: Promise[JobSpec]): Unit = {
    inFlightChanges -= id
    promise.failure(ex)
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
    def newActor: Option[ActorRef] = jobSpec.schedule.map { schedule =>
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
  case class ListJobSpecs(filter: JobSpec => Boolean, promise: Promise[Iterable[JobSpec]])
  case class GetJobSpec(id: PathId, promise: Promise[Option[JobSpec]])
  case class CreateJobSpec(jobSpec: JobSpec, promise: Promise[JobSpec])
  case class UpdateJobSpec(id: PathId, change: JobSpec => JobSpec, promise: Promise[JobSpec])
  case class DeleteJobSpec(id: PathId, promise: Promise[JobSpec])

  def props(
    repo:                    Repository[PathId, JobSpec],
    persistenceActorFactory: PathId => Props,
    schedulerActorFactory:   JobSpec => Props
  ): Props = Props(new JobSpecServiceActor(repo, persistenceActorFactory, schedulerActorFactory))
}
package dcos.metronome
package jobspec.impl

import akka.actor._
import dcos.metronome.model.{Event, JobId, JobSpec}
import dcos.metronome.repository.{LoadContentOnStartup, Repository}

import scala.collection.concurrent.TrieMap

/**
  * This actor knows all available JobSpecs (as view) and manages all available JobSpecExecutors.
  */
//noinspection AccessorLikeMethodIsUnit
class JobSpecServiceActor(
    val repo: Repository[JobId, JobSpec],
    persistenceActorFactory: JobId => Props,
    schedulerActorFactory: JobSpec => Props,
    dependencyActorFactory: JobSpec => Props
) extends LoadContentOnStartup[JobId, JobSpec] {
  import JobSpecPersistenceActor._
  import JobSpecServiceActor._

  private[impl] val allJobs = TrieMap.empty[JobId, JobSpec]

  private[impl] var inFlightChanges = Set.empty[JobId]

  private[impl] val scheduleActors = TrieMap.empty[JobId, ActorRef]
  private[impl] val dependencyActors = TrieMap.empty[JobId, ActorRef]
  private[impl] val persistenceActors = TrieMap.empty[JobId, ActorRef]

  override def receive: Receive = {
    // crud messages
    case CreateJobSpec(jobSpec) => createJobSpec(jobSpec)
    case UpdateJobSpec(id, change) => updateJobSpec(id, change)
    case DeleteJobSpec(id) => deleteJobSpec(id)
    case GetJobSpec(id) => getJobSpec(id)
    case ListJobSpecs(filter) => listJobSpecs(filter)

    // persistence ack messages
    case Created(_, jobSpec, delegate) => jobSpecCreated(jobSpec, delegate)
    case Updated(_, jobSpec, delegate) => jobSpecUpdated(jobSpec, delegate)
    case Deleted(_, jobSpec, delegate) => jobSpecDeleted(jobSpec, delegate)
    case PersistFailed(_, id, ex, delegate) => jobChangeFailed(id, ex, delegate)

    // lifetime messages
    case Terminated(ref) => handleTerminated(ref)
  }

  def getJobSpec(id: JobId): Unit = {
    sender() ! allJobs.get(id)
  }

  def listJobSpecs(filter: JobSpec => Boolean): Unit = {
    sender() ! allJobs.values.filter(filter)
  }

  def createJobSpec(jobSpec: JobSpec): Unit = {
    validJobSpec(jobSpec) {
      noSpecWithId(jobSpec) {
        noChangeInFlight(jobSpec) {
          persistenceActor(jobSpec.id) ! JobSpecPersistenceActor.Create(jobSpec, sender())
        }
      }
    }
  }

  def updateJobSpec(id: JobId, change: JobSpec => JobSpec): Unit = {
    withJob(id) { old =>
      noChangeInFlight(old) {
        validJobSpec(change(old)) {
          persistenceActor(id) ! JobSpecPersistenceActor.Update(change, sender())
        }
      }
    }
  }

  def deleteJobSpec(id: JobId): Unit = {
    withJob(id) { old =>
      noChangeInFlight(old) {
        noDependency(old) {
          persistenceActor(id) ! JobSpecPersistenceActor.Delete(old, sender())
        }
      }
    }
  }

  def withJob[T](id: JobId)(fn: JobSpec => T): Option[T] = {
    val result = allJobs.get(id).map(fn)
    if (result.isEmpty) sender() ! Status.Failure(JobSpecDoesNotExist(id))
    result
  }

  def noSpecWithId(jobSpec: JobSpec)(change: => Unit): Unit = {
    if (allJobs.contains(jobSpec.id)) sender() ! Status.Failure(JobSpecAlreadyExists(jobSpec.id))
    else {
      change
    }
  }

  def noChangeInFlight(jobSpec: JobSpec)(change: => Unit): Unit = {
    if (inFlightChanges.contains(jobSpec.id)) sender() ! Status.Failure(JobSpecChangeInFlight(jobSpec.id))
    else {
      inFlightChanges += jobSpec.id
      try {
        change
      } catch {
        case _ => inFlightChanges -= jobSpec.id
      }
    }
  }

  def noDependency(jobSpec: JobSpec)(change: => Unit): Unit = {
    try {
      JobSpec.validateSafeDelete(jobSpec.id, allJobs.values.toVector)
      change
    } catch {
      case ex: JobSpec.DependencyConflict => {
        inFlightChanges -= jobSpec.id
        sender() ! Status.Failure(ex)
      }
      case e: Exception => throw e
    }
  }

  def validJobSpec(spec: JobSpec)(change: => Unit): Unit = {
    try {
      JobSpec.validateDependencies(spec, allJobs.values.toVector)
      change
    } catch {
      case ex: JobSpec.ValidationError => sender() ! Status.Failure(ex)
    }
  }

  def addJobSpec(jobSpec: JobSpec): Option[ActorRef] = {
    allJobs += jobSpec.id -> jobSpec
    inFlightChanges -= jobSpec.id
    scheduleActor(jobSpec)
    dependencyActor(jobSpec)
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
    dependencyActor(jobSpec).foreach { actor =>
      if (jobSpec.dependencies.nonEmpty) {
        actor ! JobSpecDependencyActor.UpdateJobSpec(jobSpec)
      } else {
        //the updated spec does not have any dependencies
        context.unwatch(actor)
        context.stop(actor)
        dependencyActors -= jobSpec.id
      }
    }
    context.system.eventStream.publish(Event.JobSpecUpdated(jobSpec))
    delegate ! jobSpec
  }

  def jobSpecDeleted(jobSpec: JobSpec, delegate: ActorRef): Unit = {
    def removeFrom(map: TrieMap[JobId, ActorRef]) =
      map.remove(jobSpec.id).foreach { actorRef =>
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

  def jobChangeFailed(id: JobId, ex: Throwable, delegate: ActorRef): Unit = {
    inFlightChanges -= id
    delegate ! Status.Failure(ex)
  }

  def handleTerminated(ref: ActorRef): Unit = {
    log.error(s"Actor has terminated: $ref")
    //TODO: restart?
  }

  def persistenceActor(id: JobId): ActorRef = {
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
    def newActor: Option[ActorRef] =
      jobSpec.schedules.headOption match {
        case Some(schedule) if schedule.enabled =>
          val ref = context.actorOf(schedulerActorFactory(jobSpec), s"scheduler:${jobSpec.id.safePath}")
          context.watch(ref)
          scheduleActors += jobSpec.id -> ref
          Some(ref)
        case _ => // Nothing to do since the schedule is disabled
          None
      }
    scheduleActors.get(jobSpec.id) orElse newActor
  }

  /**
    * Launches or retrieves a dependency actor that executes the job if its dependency finished.
    * @param jobSpec The job spec that should be managed.
    * @return The dependency actor or none if no dependencies are declared.
    */
  def dependencyActor(jobSpec: JobSpec): Option[ActorRef] = {
    def newActor: Option[ActorRef] =
      if (jobSpec.dependencies.nonEmpty) {
        val ref = context.actorOf(dependencyActorFactory(jobSpec), s"dependency:${jobSpec.id.safePath}")
        context.watch(ref)
        dependencyActors += jobSpec.id -> ref
        Some(ref)
      } else None

    dependencyActors.get(jobSpec.id) orElse newActor
  }

  def initialize(jobs: List[JobSpec]): Unit = {
    log.info(s"Loaded JobSpecs: $jobs")
    jobs.foreach(addJobSpec)
  }
}

object JobSpecServiceActor {

  //crud messages
  sealed trait Message
  sealed trait Modification extends Message
  case class ListJobSpecs(filter: JobSpec => Boolean)
  case class GetJobSpec(id: JobId)
  case class CreateJobSpec(jobSpec: JobSpec) extends Modification
  case class UpdateJobSpec(id: JobId, change: JobSpec => JobSpec) extends Modification
  case class DeleteJobSpec(id: JobId) extends Modification

  def props(
      repo: Repository[JobId, JobSpec],
      persistenceActorFactory: JobId => Props,
      schedulerActorFactory: JobSpec => Props,
      dependencyActorFactory: JobSpec => Props
  ): Props =
    Props(new JobSpecServiceActor(repo, persistenceActorFactory, schedulerActorFactory, dependencyActorFactory))
}

package dcos.metronome.jobspec.impl

import akka.actor._
import dcos.metronome.{ JobSpecChangeInFlight, JobSpecAlreadyExists }
import dcos.metronome.model.JobSpec
import mesosphere.marathon.state.PathId

import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise

/**
 * This actor knows all available JobSpecs (as view) and manages all available JobSpecExecutors.
 */
class JobSpecServiceActor(repo: JobSpecRepository) extends Actor with ActorLogging {
  import JobSpecServiceActor._

  import context.dispatcher

  private val allJobs = TrieMap.empty[PathId, JobSpec]
  private val inFlightChanges = TrieMap.empty[PathId, JobSpec]
  private val jobSpecActors = TrieMap.empty[PathId, ActorRef]

  override def receive: Receive = {
    //crud messages
    case CreateJobSpec(jobSpec, promise)    => createJobSpec(jobSpec, promise)
    case UpdateJobSpec(id, change, promise) => updateJobSpec(id, change, promise)
    case DeleteJobSpec(id, promise)         => deleteJobSpec(id, promise)
    case GetJobSpec(id, promise)            => getJobSpec(id, promise)
    case ListJobSpecs(filter, promise)      => listJobSpecs(filter, promise)

    //result messages
    case JobSpecCreated(jobSpec)            => updateJobView(jobSpec)
    case JobSpecUpdated(jobSpec)            => updateJobView(jobSpec)
    case JobSpecDeleted(jobSpec)            => deleteFromJobView(jobSpec)

    //lifetime messages
    case Terminated(ref)                    => handleTerminated(ref)
  }

  def updateJobView(jobSpec: JobSpec): Unit = {
    allJobs += jobSpec.id -> jobSpec
    inFlightChanges -= jobSpec.id
  }

  def deleteFromJobView(jobSpec: JobSpec): Unit = {
    allJobs -= jobSpec.id
    inFlightChanges -= jobSpec.id
    jobSpecActors.remove(jobSpec.id).foreach { actorRef =>
      context.unwatch(actorRef)
      context.stop(actorRef)
    }
  }

  def getJobSpec(id: PathId, promise: Promise[Option[JobSpec]]): Unit = {
    promise.success(allJobs.get(id))
  }

  def listJobSpecs(filter: JobSpec => Boolean, promise: Promise[Iterable[JobSpec]]): Unit = {
    promise.success(allJobs.values.filter(filter))
  }

  def createJobSpec(jobSpec: JobSpec, promise: Promise[JobSpec]): Unit = {
    noChangeInFlight(jobSpec, promise) {
      noSpecWithId(jobSpec, promise) {
        val ref = context.actorOf(JobSpecSchedulerActor.props(jobSpec, Some(promise), repo))
        context.watch(ref)
        jobSpecActors += jobSpec.id -> ref
        val service = self
        promise.future.onSuccess { case spec => service ! JobSpecCreated(spec) }
      }
    }
  }

  def updateJobSpec(id: PathId, change: JobSpec => JobSpec, promise: Promise[JobSpec]): Unit = {
    withJob(id) {
      case (old, schedulerActor) =>
        noChangeInFlight(old, promise) {
          schedulerActor ! "TODO"
          val service = self
          promise.future.onSuccess { case spec => service ! JobSpecUpdated(spec) }
        }
    }
  }

  def deleteJobSpec(id: PathId, promise: Promise[JobSpec]): Unit = {
    withJob(id) {
      case (old, schedulerActor) =>
        noChangeInFlight(old, promise) {
          schedulerActor ! "TODO"
          val service = self
          promise.future.onSuccess { case spec => service ! JobSpecDeleted(spec) }
        }
    }
  }

  def withJob[T](id: PathId)(fn: (JobSpec, ActorRef) => T): Option[T] = {
    for {
      job <- allJobs.get(id)
      actor <- jobSpecActors.get(id)
    } yield fn(job, actor)
  }

  def noSpecWithId(jobSpec: JobSpec, promise: Promise[JobSpec])(change: => Unit): Unit = {
    if (allJobs.contains(jobSpec.id)) promise.failure(JobSpecChangeInFlight(jobSpec.id)) else {
      change
    }
  }

  def noChangeInFlight[T](jobSpec: JobSpec, promise: Promise[T])(change: => Unit): Unit = {
    if (inFlightChanges.contains(jobSpec.id)) promise.failure(JobSpecChangeInFlight(jobSpec.id)) else {
      inFlightChanges += jobSpec.id -> jobSpec
      change
    }
  }

  def handleTerminated(ref: ActorRef): Unit = {
    log.error(s"Actor has terminated: $ref")
    //TODO: restart?
  }
}

object JobSpecServiceActor {

  //crud messages
  case class ListJobSpecs(filter: JobSpec => Boolean, promise: Promise[Iterable[JobSpec]])
  case class GetJobSpec(id: PathId, promise: Promise[Option[JobSpec]])
  case class CreateJobSpec(jobSpec: JobSpec, promise: Promise[JobSpec])
  case class UpdateJobSpec(id: PathId, change: JobSpec => JobSpec, promise: Promise[JobSpec])
  case class DeleteJobSpec(id: PathId, promise: Promise[JobSpec])

  //ack messages
  case class JobSpecCreated(jobSpec: JobSpec)
  case class JobSpecUpdated(jobSpec: JobSpec)
  case class JobSpecDeleted(jobSpec: JobSpec)

  def props(repo: JobSpecRepository): Props = Props(new JobSpecServiceActor(repo))
}
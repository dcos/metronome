package dcos.metronome
package jobspec.impl

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.mesosphere.usi.async.ExecutionContexts
import dcos.metronome.jobspec.impl.JobSpecServiceActor._
import dcos.metronome.jobspec.{JobSpecConfig, JobSpecService}
import dcos.metronome.model.{JobId, JobSpec}
import mesosphere.marathon.metrics.Metrics

import scala.concurrent.Future

class JobSpecServiceDelegate(config: JobSpecConfig, actorRef: ActorRef, metrics: Metrics) extends JobSpecService {

  private val createJobSpecTimeMetric = metrics.timer("debug.job-spec.operations.create.duration")
  private val getJobSpecTimeMetric = metrics.timer("debug.job-spec.operations.get.duration")
  private val updateJobSpecTimeMetric = metrics.timer("debug.job-spec.operations.update.duration")
  private val listJobSpecTimeMetric = metrics.timer("debug.job-spec.operations.list.duration")
  private val deleteJobSpecTimeMetric = metrics.timer("debug.job-spec.operations.delete.duration")

  implicit val timeout: Timeout = config.askTimeout

  override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] =
    createJobSpecTimeMetric {
      actorRef.ask(CreateJobSpec(jobSpec)).mapTo[JobSpec]
    }

  override def getJobSpec(id: JobId): Future[Option[JobSpec]] =
    getJobSpecTimeMetric {
      actorRef.ask(GetJobSpec(id)).mapTo[Option[JobSpec]]
    }

  override def updateJobSpec(id: JobId, update: JobSpec => JobSpec): Future[JobSpec] =
    updateJobSpecTimeMetric {
      actorRef.ask(UpdateJobSpec(id, update)).mapTo[JobSpec]
    }

  override def listJobSpecs(filter: JobSpec => Boolean): Future[Iterable[JobSpec]] =
    listJobSpecTimeMetric {
      actorRef.ask(ListJobSpecs(filter)).mapTo[Iterable[JobSpec]]
    }

  override def deleteJobSpec(id: JobId): Future[JobSpec] =
    deleteJobSpecTimeMetric {
      actorRef.ask(DeleteJobSpec(id)).mapTo[JobSpec]
    }

  override def transaction(updater: Seq[JobSpec] => Option[Modification]): Future[Option[JobSpec]] =
    updateJobSpecTimeMetric {
      actorRef.ask(Transaction(updater)).mapTo[JobSpec].map(Option.apply)(ExecutionContexts.callerThread)
    }
}

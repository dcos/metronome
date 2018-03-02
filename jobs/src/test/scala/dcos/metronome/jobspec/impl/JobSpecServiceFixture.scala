package dcos.metronome.jobspec.impl

import java.time.Clock

import dcos.metronome.{ JobSpecAlreadyExists, JobSpecDoesNotExist }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobId, JobSpec, ScheduleSpec }

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.control.NonFatal

object JobSpecServiceFixture {

  def simpleJobSpecService(testClock: Clock = Clock.systemUTC()): JobSpecService = new JobSpecService {
    val specs = TrieMap.empty[JobId, JobSpec]
    import Future._
    override def getJobSpec(id: JobId): Future[Option[JobSpec]] = successful(specs.get(id))

    override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] = {
      specs.get(jobSpec.id) match {
        case Some(_) =>
          failed(JobSpecAlreadyExists(jobSpec.id))
        case None =>
          specs += jobSpec.id -> jobSpecWithMockedTime(jobSpec)
          successful(jobSpec)
      }
    }

    private def jobSpecWithMockedTime(jobSpec: JobSpec): JobSpec = jobSpec.copy(schedules = jobSpec.schedules.map(s => new ScheduleSpec(s.id, s.cron, s.timeZone, s.startingDeadline, s.concurrencyPolicy, s.enabled) {
      override def clock: Clock = testClock
    }))

    override def updateJobSpec(id: JobId, update: (JobSpec) => JobSpec): Future[JobSpec] = {
      specs.get(id) match {
        case Some(spec) =>
          try {
            val changed = update(spec)
            specs.update(id, jobSpecWithMockedTime(changed))
            successful(changed)
          } catch {
            case NonFatal(ex) => failed(ex)
          }
        case None => failed(JobSpecDoesNotExist(id))
      }
    }

    override def listJobSpecs(filter: (JobSpec) => Boolean): Future[Iterable[JobSpec]] = {
      successful(specs.values.filter(filter))
    }

    override def deleteJobSpec(id: JobId): Future[JobSpec] = {
      specs.get(id) match {
        case Some(spec) =>
          specs -= id
          successful(spec)
        case None => failed(JobSpecDoesNotExist(id))
      }
    }
  }
}

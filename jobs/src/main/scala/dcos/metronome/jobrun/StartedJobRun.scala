package dcos.metronome.jobrun

import dcos.metronome.model.{ JobStatus, JobRun }
import scala.concurrent.Future

case class StartedJobRun(
  jobRun:   JobRun, //the related job run
  complete: Future[JobStatus] //this future completes, when the job run is finished
)


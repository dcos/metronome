package dcos.metronome.jobrun

import dcos.metronome.model.{ JobResult, JobRun }
import scala.concurrent.Future

case class StartedJobRun(
  jobRun:   JobRun, //the related job run
  complete: Future[JobResult] //this future completes, when the job run is finished
)


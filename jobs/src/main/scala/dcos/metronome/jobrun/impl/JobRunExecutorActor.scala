package dcos.metronome.jobrun.impl

import akka.actor.Actor
import dcos.metronome.model.{ JobRun, JobSpec }

/**
 * Handles one job run from start until the job either completes successful or failed.
 *
 * @param spec the specification for this run.
 * @param run the related job run  object.
 */
class JobRunExecutorActor(spec: JobSpec, run: JobRun) extends Actor {

  override def receive: Receive = ???

}

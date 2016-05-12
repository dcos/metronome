package dcos.metronome.jobspec.impl

import akka.actor.Actor
import dcos.metronome.model.JobSpec

/**
 * Manages one JobSpec.
 * If the JobSpec has a schedule, the schedule is triggered in this actor.
 */
class JobSpecSchedulerActor(spec: JobSpec) extends Actor {

  override def receive: Receive = ???

}

package dcos.metronome.jobspec

import akka.actor.ActorSystem
import dcos.metronome.jobspec.impl.{ JobSpecServiceDelegate, JobSpecServiceActor, JobSpecRepository }

class JobSpecModule(config: JobSpecConfig, actorSystem: ActorSystem) {

  val repo: JobSpecRepository = new JobSpecRepository

  //TODO: start when we get elected
  val serviceActor = actorSystem.actorOf(JobSpecServiceActor.props(repo))

  def jobSpecService: JobSpecService = new JobSpecServiceDelegate(config, serviceActor)

}

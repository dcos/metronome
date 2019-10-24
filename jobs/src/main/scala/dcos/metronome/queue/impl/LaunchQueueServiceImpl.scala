package dcos.metronome
package queue.impl

import dcos.metronome.model.QueuedJobRunInfo
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.task.tracker.InstanceTracker
import mesosphere.marathon.core.task.tracker.InstanceTracker.SpecInstances
import mesosphere.marathon.state.{ AbsolutePathId, AppDefinition }

class LaunchQueueServiceImpl(instanceTracker: InstanceTracker) extends LaunchQueueService {

  override def list(): Seq[QueuedJobRunInfo] = {
    toModel(instanceTracker.instancesBySpecSync.instancesMap)
  }

  private[this] def toModel(instanceMap: Map[AbsolutePathId, SpecInstances]): Seq[QueuedJobRunInfo] = {
    instanceMap.flatMap{ case (id, instances) => mapRunSpecs(id, instances) }.toIndexedSeq
  }

  private[this] def mapRunSpecs(id: AbsolutePathId, instances: SpecInstances): Seq[QueuedJobRunInfo] = {
    import dcos.metronome.jobrun.impl.QueuedJobRunConverter.RunSpecToJobRunSpec

    instances.instanceMap.values.map{ instance =>
      val jobRunSpec = instance.runSpec match {
        case app: AppDefinition => app.toModel
        case runSpec =>
          throw new IllegalArgumentException(s"Unexpected runSpec type - jobs are translated to Apps on Marathon level, got $runSpec")
      }

      //      val configRef = RunSpecConfigRef
      //      launchQueue.getDelay(instance.runSpec.configRef).delay.get.deadline

      // TODO AN: This is wrong, but at the moment we don't have a backoff anyway.
      val backoffUntil = instance.state.since

      QueuedJobRunInfo(
        id = id,
        backOffUntil = backoffUntil,
        run = jobRunSpec,
        acceptedResourceRoles = instance.runSpec.acceptedResourceRoles)
    }.toIndexedSeq
  }

}

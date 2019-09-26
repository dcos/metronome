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

    //    // timeout is enforced in LaunchQueue
    //    Await.result(launchQueue.list, Duration.Inf).filter(_.inProgress).map(_.toModel)
  }

  private[this] def toModel(instanceMap: Map[AbsolutePathId, SpecInstances]): Seq[QueuedJobRunInfo] = {
    instanceMap.map{ case (id, instances) => mapRunSpec(id, instances) }.toIndexedSeq
  }

  private[this] def mapRunSpec(id: AbsolutePathId, instances: SpecInstances): QueuedJobRunInfo = {
    import dcos.metronome.jobrun.impl.QueuedJobRunConverter.RunSpecToJobRunSpec

    // TODO AN: What about multiple instances?
    val instance = instances.instanceMap.values.head
    val jobRunSpec = instance.runSpec match {
      case app: AppDefinition => app.toModel
      case runSpec =>
        throw new IllegalArgumentException(s"Unexpected runSpec type - jobs are translated to Apps on Marathon level, got $runSpec")
    }
    // TODO AN:  This is wrong
    val backoffUntil = instance.state.since

    QueuedJobRunInfo(
      id = id,
      backOffUntil = backoffUntil,
      run = jobRunSpec,
      acceptedResourceRoles = instance.runSpec.acceptedResourceRoles)
  }

}

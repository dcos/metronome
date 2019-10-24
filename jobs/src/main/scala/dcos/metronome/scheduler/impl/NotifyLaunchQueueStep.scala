package dcos.metronome.scheduler.impl

import akka.Done
import com.google.inject.Provider
import mesosphere.marathon.core.instance.update.{ InstanceChange, InstanceChangeHandler }
import mesosphere.marathon.core.launchqueue.LaunchQueue
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class NotifyLaunchQueueStep(launchQueueProvider: Provider[LaunchQueue]) extends InstanceChangeHandler {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def name: String = "NotifyLaunchQueueStep"
  override def metricName: String = "NotifyLaunchQueueStep"

  override def process(instanceChange: InstanceChange): Future[Done] = {
    log.info(s"Received update with condition ${instanceChange.condition} on instance ${instanceChange.instance} ")
    launchQueueProvider.get().notifyOfInstanceUpdate(instanceChange)
    Future.successful(Done)
  }
}

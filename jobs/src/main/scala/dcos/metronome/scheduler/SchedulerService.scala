package dcos.metronome
package scheduler

import scala.concurrent.Future

/**
  * The Service that will run the scheduler driver.
  */
trait SchedulerService {
  /**
    * Run the service and offer leadership. This will eventually start the driver when we are elected as leader.
    * This call will block until the service is shutdown.
    */
  def run(): Unit

  /**
    * Shutdown the service and stop the driver.
    */
  def shutdown(): Unit
}

/**
  * PrePostDriverCallback is implemented by callback receivers which have to listen for driver
  * start/stop events
  */
trait PrePostDriverCallback {
  /**
    * Will get called _before_ the driver is running, but after migration.
    */
  def preDriverStarts: Future[Unit]

  /**
    * Will get called _after_ the driver terminated
    */
  def postDriverTerminates: Future[Unit]
}

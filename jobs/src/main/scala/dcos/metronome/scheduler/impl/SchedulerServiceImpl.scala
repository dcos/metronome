package dcos.metronome
package scheduler.impl

import java.util.concurrent.CountDownLatch

import akka.util.Timeout
import dcos.metronome.migration.Migration
import dcos.metronome.scheduler.{ PeriodicOperations, PrePostDriverCallback, SchedulerConfig, SchedulerService }
import mesosphere.marathon.core.election.{ ElectionCandidate, ElectionService }
import mesosphere.marathon.core.leadership.LeadershipCoordinator
import mesosphere.marathon.SchedulerDriverFactory
import mesosphere.marathon.core.storage.store.PersistenceStore
import org.apache.mesos.SchedulerDriver
import org.slf4j.LoggerFactory

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.Failure

/**
  * Wrapper class for the scheduler
  */
private[scheduler] class SchedulerServiceImpl(
  persistenceStore:       PersistenceStore[_, _, _],
  leadershipCoordinator:  LeadershipCoordinator,
  config:                 SchedulerConfig,
  electionService:        ElectionService,
  prePostDriverCallbacks: Seq[PrePostDriverCallback],
  driverFactory:          SchedulerDriverFactory,
  migration:              Migration,
  periodicOperations:     PeriodicOperations)
    extends SchedulerService with ElectionCandidate {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val log = LoggerFactory.getLogger(getClass.getName)

  implicit private[this] val zkTimeout = config.zkTimeout
  implicit private[this] val timeout: Timeout = 5.seconds

  private[this] val isRunningLatch = new CountDownLatch(1)

  // This is a little ugly as we are using a mutable variable. But drivers can't
  // be reused (i.e. once stopped they can't be started again. Thus,
  // we have to allocate a new driver before each run or after each stop.
  private[this] var driver: Option[SchedulerDriver] = None

  //Begin Service interface

  override def run(): Unit = {
    // The first thing we do is offer our leadership.
    electionService.offerLeadership(this)

    // Block on the latch which will be countdown only when shutdown has been
    // triggered. This is to prevent the returned Future from completing
    Future {
      isRunningLatch.await()
    }
  }

  override def shutdown(): Unit = synchronized {
    log.info("Shutdown triggered")

    log.info("Stopping Driver")
    stopDriver()

    log.info("Cancelling periodic operations")
    periodicOperations.cancel()

    // The countdown latch blocks run() from exiting. Counting down the latch removes the block.
    log.debug("Removing the blocking of run()")
    isRunningLatch.countDown()
  }

  private[this] def stopDriver(): Unit = synchronized {
    // Stopping the driver will cause the driver run() method to return.
    driver.foreach(_.stop(true)) // failover = true
    driver = None
  }

  //End Service interface

  //Begin ElectionCandidate interface

  def startLeadership(): Unit = synchronized {
    log.info("As new leader running the driver")

    // allow interactions with the persistence store
    persistenceStore.markOpen()

    // execute tasks, only the leader is allowed to
    migration.migrate()

    log.info(s"""Call preDriverStarts callbacks on ${prePostDriverCallbacks.mkString(", ")}""")
    Await.result(
      Future.sequence(prePostDriverCallbacks.map(_.preDriverStarts)),
      config.leaderPreparationTimeout)
    log.info("Finished preDriverStarts callbacks")

    // start all leadership coordination actors
    Await.result(leadershipCoordinator.prepareForStart(), config.maxActorStartupTime)

    log.info("Creating new driver")
    driver = Some(driverFactory.createDriver())

    log.info("Schedule periodic operations")
    periodicOperations.schedule()

    // The following block asynchronously runs the driver. Note that driver.run()
    // blocks until the driver has been stopped (or aborted).
    Future {
      scala.concurrent.blocking {
        driver.foreach(_.run())
      }
    } onComplete { result =>
      synchronized {
        driver = None

        log.info(s"Driver future completed with result=$result.")
        result match {
          case Failure(t) => log.error("Exception while running driver", t)
          case _          =>
        }

        // tell leader election that we step back, but want to be re-elected if isRunning is true.
        electionService.abdicateLeadership()

        log.info(s"Call postDriverRuns callbacks on ${prePostDriverCallbacks.mkString(", ")}")
        Await.result(Future.sequence(prePostDriverCallbacks.map(_.postDriverTerminates)), config.zkTimeout)
        log.info(s"Finished postDriverRuns callbacks")
      }
    }
  }

  def stopLeadership(): Unit = synchronized {
    log.info("Lost leadership")

    leadershipCoordinator.stop()
    periodicOperations.cancel()

    if (driver.isDefined) {
      // Our leadership has been defeated. Thus, stop the driver.
      // Note that abdication command will be ran upon driver shutdown which
      // will then offer leadership again.
      stopDriver()
    } else {
      electionService.offerLeadership(this)
    }
  }

  //End ElectionCandidate interface
}

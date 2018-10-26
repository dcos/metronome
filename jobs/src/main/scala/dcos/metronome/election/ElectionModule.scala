package dcos.metronome
package election

import akka.actor.ActorSystem
import akka.event.EventStream
import com.codahale.metrics.MetricRegistry
import mesosphere.chaos.http.HttpConf
import mesosphere.marathon.MarathonConf
import mesosphere.marathon.core.base.ShutdownHooks
import mesosphere.marathon.core.election.ElectionService
import mesosphere.marathon.core.election.impl.{ ExponentialBackoff, PseudoElectionService, TwitterCommonsElectionService }
import mesosphere.marathon.metrics.Metrics

/**
  *
  *  Copied from marathon 1.4.
  *  In order to make the changes to leader election to prevent deadlock.  This is for Metronome 0.4.x.   Metronome 0.5.x is
  *  based on a version of Marathon that has these changes incorporated.
  *
  *  This class is copied over to pull in the changed CuratorElectionService.   This is the only change from the Marathon version.
  */
class ElectionModule(
  config:        MarathonConf,
  system:        ActorSystem,
  eventStream:   EventStream,
  http:          HttpConf,
  metrics:       Metrics       = new Metrics(new MetricRegistry),
  hostPort:      String,
  shutdownHooks: ShutdownHooks) {
  private lazy val backoff = new ExponentialBackoff(name = "offerLeadership")
  lazy val service: ElectionService = if (config.highlyAvailable()) {
    config.leaderElectionBackend.get match {
      case Some("twitter_commons") =>
        new TwitterCommonsElectionService(
          config,
          system,
          eventStream,
          http,
          metrics,
          hostPort,
          backoff,
          shutdownHooks)
      case Some("curator") =>
        new CuratorElectionService(
          config,
          system,
          eventStream,
          http,
          metrics,
          hostPort,
          backoff,
          shutdownHooks)
      case backend: Option[String] =>
        throw new IllegalArgumentException(s"Leader election backend $backend not known!")
    }
  } else {
    new PseudoElectionService(
      config,
      system,
      eventStream,
      metrics,
      hostPort,
      backoff,
      shutdownHooks)
  }
}

package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models.QueuedJobRunMapWrites
import dcos.metronome.api.{ ApiConfig, Authorization }
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class LaunchQueueController(
  cc:            ControllerComponents,
  queueService:  LaunchQueueService,
  metrics:       Metrics,
  authenticator: Authenticator,
  authorizer:    Authorizer,
  config:        ApiConfig)(implicit ec: ExecutionContext) extends Authorization(cc, metrics, authenticator, authorizer, config) {

  def queue() = measured {
    AuthorizedAction.apply { implicit request =>
      Ok(QueuedJobRunMapWrites.writes(queueService.list().filter(request.isAllowed).groupBy(_.jobId)))
    }
  }
}

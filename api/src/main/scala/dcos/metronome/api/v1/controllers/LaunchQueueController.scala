package dcos.metronome
package api.v1.controllers

import akka.stream.Materializer
import dcos.metronome.api.v1.models.QueuedJobRunMapWrites
import dcos.metronome.api.{ ApiConfig, Authorization }
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class LaunchQueueController(cc: ControllerComponents)(
  implicit
  ec: ExecutionContext, queueService: LaunchQueueService, metrics: Metrics,
  authenticator: Authenticator,
  authorizer:    Authorizer,
  config:        ApiConfig,
  mat:           Materializer) extends Authorization(cc) {

  def queue() = measured {
    AuthorizedAction.apply { implicit request =>
      Ok(QueuedJobRunMapWrites.writes(queueService.list().filter(request.isAllowed).groupBy(_.jobId)))
    }
  }
}

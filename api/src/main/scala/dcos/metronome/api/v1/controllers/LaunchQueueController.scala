package dcos.metronome
package api.v1.controllers

import akka.stream.Materializer
import dcos.metronome.api.v1.models.QueuedJobRunMapWrites
import dcos.metronome.api.{ ApiConfig, Authorization }
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import play.api.mvc.{ AnyContent, BodyParser, PlayBodyParsers }

class LaunchQueueController(
  queueService:          LaunchQueueService,
  val authenticator:     Authenticator,
  val authorizer:        Authorizer,
  val config:            ApiConfig,
  val mat:               Materializer,
  val defaultBodyParser: BodyParser[AnyContent]) extends Authorization {

  def queue() = AuthorizedAction.apply { implicit request =>
    Ok(QueuedJobRunMapWrites.writes(queueService.list().filter(request.isAllowed).groupBy(_.jobId)))
  }
}

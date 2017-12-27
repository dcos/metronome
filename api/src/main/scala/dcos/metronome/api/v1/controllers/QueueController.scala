package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ Authorization, RestController }
import dcos.metronome.queue.QueueService
import dcos.metronome.api.v1.models.QueuedTaskInfoWrites
import play.api.libs.json.Json
import play.api.mvc.Action

class QueueController(queueService: QueueService) extends RestController {

  def queue() = Action {
    Ok(Json.toJson(queueService.list().map(QueuedTaskInfoWrites.writes(_))))
  }
}

package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ Authorization, RestController }
import dcos.metronome.queue.QueueService
import dcos.metronome.api.v1.models.QueuedTaskInfoMapWrites
import play.api.mvc.Action

class QueueController(queueService: QueueService) extends RestController {

  def queue() = Action {
    Ok(QueuedTaskInfoMapWrites.writes(queueService.listGroupByJobId()))

  }
}

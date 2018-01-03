package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ Authorization, RestController }
import dcos.metronome.queue.LaunchQueueService
import dcos.metronome.api.v1.models.JsonConverters._
import play.api.mvc.Action

class LaunchQueueController(queueService: LaunchQueueService) extends RestController {

  def queue() = Action {
    Ok(queueToJson(queueService.list.groupBy(_.jobid)))
  }
}

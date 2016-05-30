package dcos.metronome.api.v1.controllers

import play.api.mvc.{ Action, Controller }

class ApplicationController extends Controller {

  def ping = Action { Ok("pong") }

}

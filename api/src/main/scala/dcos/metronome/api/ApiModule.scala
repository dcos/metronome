package dcos.metronome.api

import controllers.Assets
import dcos.metronome.api.v1.controllers.{ GreeterController, TickingController }
import dcos.metronome.greeting.GreetingService
import mesosphere.marathon.core.auth.AuthModule
import mesosphere.marathon.core.plugin.PluginManager
import play.api.http.HttpErrorHandler
import play.api.routing.Router
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import com.softwaremill.macwire._
import dcos.metronome.ticking.TickingService
import router.Routes

class ApiModule(
    greetingService: GreetingService,
    tickingService: TickingService,
    pluginManager: PluginManager,
    httpErrorHandler: HttpErrorHandler,
    assets: Assets) {

  lazy val greeterController = wire[GreeterController]
  lazy val tickingController = wire[TickingController]

  lazy val authModule: AuthModule = wire[AuthModule]

  lazy val authorizer: Authorizer = authModule.authorizer

  lazy val authenticator: Authenticator = authModule.authenticator

  lazy val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }
}

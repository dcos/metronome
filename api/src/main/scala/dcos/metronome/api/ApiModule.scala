package dcos.metronome
package api

import com.softwaremill.macwire._
import controllers.Assets
import dcos.metronome.api.v0.controllers.ScheduledJobSpecController
import dcos.metronome.api.v1.controllers._
import dcos.metronome.jobinfo.JobInfoService
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.auth.AuthModule
import mesosphere.marathon.core.base.ActorsModule
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import play.api.http.HttpErrorHandler
import play.api.routing.Router
import router.Routes

class ApiModule(
  config:             ApiConfig,
  jobSpecService:     JobSpecService,
  jobRunService:      JobRunService,
  jobInfoService:     JobInfoService,
  pluginManager:      PluginManager,
  httpErrorHandler:   HttpErrorHandler,
  assets:             Assets,
  launchQueueService: LaunchQueueService,
  actorsModule:       ActorsModule) {

  lazy val mat = actorsModule.materializer

  lazy val applicationController = wire[ApplicationController]

  lazy val jobsSpecController = wire[JobSpecController]

  lazy val jobRunsController = wire[JobRunController]

  lazy val jobSchedulerController = wire[JobScheduleController]

  lazy val scheduledJobSchedulerController = wire[ScheduledJobSpecController]

  lazy val authModule: AuthModule = wire[AuthModule]

  lazy val authorizer: Authorizer = authModule.authorizer

  lazy val authenticator: Authenticator = authModule.authenticator

  lazy val launchQueueController = wire[LaunchQueueController]

  lazy val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }
}

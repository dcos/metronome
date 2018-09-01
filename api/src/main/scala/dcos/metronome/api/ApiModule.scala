package dcos.metronome
package api

import akka.stream.ActorMaterializer
import com.softwaremill.macwire._
import controllers.Assets
import dcos.metronome.api.v0.controllers.ScheduledJobSpecController
import dcos.metronome.api.v1.controllers._
import dcos.metronome.jobinfo.JobInfoService
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.core.auth.AuthModule
import mesosphere.marathon.core.base.ActorsModule
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import play.api.http.HttpErrorHandler
import play.api.mvc.ControllerComponents
import play.api.routing.Router
import router.Routes

import scala.concurrent.ExecutionContext

class ApiModule(controllerComponents: ControllerComponents, assets: Assets, httpErrorHandler: HttpErrorHandler)(
  implicit
  ec:                 ExecutionContext,
  config:             ApiConfig,
  jobSpecService:     JobSpecService,
  jobRunService:      JobRunService,
  jobInfoService:     JobInfoService,
  pluginManager:      PluginManager,
  launchQueueService: LaunchQueueService,
  actorsModule:       ActorsModule,
  metricsModule:      MetricsModule) {

  lazy val authModule: AuthModule = new AuthModule(pluginManager)

  implicit lazy val authorizer: Authorizer = authModule.authorizer
  implicit lazy val authenticator: Authenticator = authModule.authenticator
  implicit lazy val metrics: Metrics = metricsModule.metrics
  implicit lazy val mat: ActorMaterializer = actorsModule.materializer

  lazy val applicationController = new ApplicationController(controllerComponents)
  lazy val jobsSpecController = new JobSpecController(controllerComponents)
  lazy val jobRunsController = new JobRunController(controllerComponents)
  lazy val jobSchedulerController = new JobScheduleController(controllerComponents)
  lazy val scheduledJobSchedulerController = new ScheduledJobSpecController(controllerComponents)
  lazy val launchQueueController = new LaunchQueueController(controllerComponents)

  lazy val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }
}

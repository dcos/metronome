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
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.core.auth.AuthModule
import mesosphere.marathon.core.base.ActorsModule
import mesosphere.marathon.core.election.ElectionService
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.plugin.auth.{Authenticator, Authorizer}
import play.api.http.HttpErrorHandler
import play.api.mvc.ControllerComponents
import play.api.routing.Router
import router.Routes

import scala.concurrent.ExecutionContext

class ApiModule(
    controllerComponents: ControllerComponents,
    assets: Assets,
    httpErrorHandler: HttpErrorHandler,
    config: JobsConfig with ApiConfig,
    jobSpecService: JobSpecService,
    jobRunService: JobRunService,
    jobInfoService: JobInfoService,
    pluginManager: PluginManager,
    launchQueueService: LaunchQueueService,
    actorsModule: ActorsModule,
    metricsModule: MetricsModule,
    electionService: ElectionService
)(implicit ec: ExecutionContext) {

  lazy val authModule: AuthModule = new AuthModule(pluginManager)

  lazy val authorizer: Authorizer = authModule.authorizer
  lazy val authenticator: Authenticator = authModule.authenticator
  lazy val metrics: Metrics = metricsModule.metrics

  lazy val applicationController: ApplicationController = wire[ApplicationController]
  lazy val jobsSpecController: JobSpecController = wire[JobSpecController]
  lazy val jobRunsController: JobRunController = wire[JobRunController]
  lazy val jobSchedulerController: JobScheduleController = wire[JobScheduleController]
  lazy val scheduledJobSchedulerController: ScheduledJobSpecController = wire[ScheduledJobSpecController]
  lazy val launchQueueController: LaunchQueueController = wire[LaunchQueueController]

  lazy val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/" // scalafix:ok
    wire[Routes]
  }
}

package dcos.metronome

import java.time.Clock

import akka.actor.ActorSystem
import com.softwaremill.macwire._
import com.typesafe.config.ConfigFactory
import controllers.Assets
import dcos.metronome.api.v1.LeaderProxyFilter
import dcos.metronome.api.{ ApiModule, ErrorHandler }
import kamon.Kamon
import mesosphere.marathon.metrics.Metrics
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig

import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClient, AhcWSClientConfig }
import play.api.libs.ws.{ WSClient, WSConfigParser }
import play.api.mvc.EssentialFilter
import play.api.routing.Router

import scala.concurrent.Future

/**
  * Application loader that wires up the application dependencies using Macwire
  */
class JobApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = {
    val jobComponents = new JobComponents(context)

    Kamon.start(jobComponents.configuration.underlying)
    Metrics.start(jobComponents.actorSystem)

    Future {
      jobComponents.schedulerService.run()
    }(scala.concurrent.ExecutionContext.global)

    jobComponents.application
  }
}

class JobComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents {
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }
  lazy val assets: Assets = wire[Assets]

  lazy val clock: Clock = Clock.systemUTC()

  override lazy val httpErrorHandler = new ErrorHandler

  private[this] lazy val jobsModule: JobsModule = wire[JobsModule]

  private[this] lazy val apiModule: ApiModule = new ApiModule(
    config,
    jobsModule.jobSpecModule.jobSpecService,
    jobsModule.jobRunModule.jobRunService,
    jobsModule.jobInfoModule.jobInfoService,
    jobsModule.pluginManger,
    httpErrorHandler,
    assets,
    jobsModule.queueModule.launchQueueService,
    jobsModule.actorsModule)

  def schedulerService = jobsModule.schedulerModule.schedulerService

  lazy val wsClient: WSClient = {
    val parser = new WSConfigParser(configuration.underlying, environment.classLoader)
    val config = new AhcWSClientConfig(wsClientConfig = parser.parse())
    val builder = new AhcConfigBuilder(config)
    // org.asynchttpclient.AsyncHttpClientConfig.AdditionalChannelInitializer
    val logging = new AsyncHttpClientConfig.AdditionalChannelInitializer() {
      override def initChannel(channel: play.shaded.ahc.io.netty.channel.Channel): Unit = {
        channel.pipeline..addFirst("log", new io.netty.handler.logging.LoggingHandler(classOf[WSClient]))
      }
    }
    val ahcBuilder = builder.configure()
    // play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig.AdditionalChannelInitializer
    ahcBuilder.setHttpAdditionalChannelInitializer(logging)
    val ahcConfig = ahcBuilder.build()
    new AhcWSClient(ahcConfig)
  }

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(
    new LeaderProxyFilter(wsClient, jobsModule.schedulerModule.electionService, config))

  override def router: Router = apiModule.router

  lazy val config = new MetronomeConfig(configuration)
}

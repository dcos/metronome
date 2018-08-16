package dcos.metronome

import java.time.Clock

import controllers.AssetsComponents
import com.softwaremill.macwire._
import dcos.metronome.api.v1.LeaderProxyFilter
import dcos.metronome.api.{ ApiModule, ErrorHandler }
import kamon.Kamon
import mesosphere.marathon.metrics.Metrics
import play.shaded.ahc.org.asynchttpclient.{ AsyncHttpClientConfig, DefaultAsyncHttpClient }
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClient, AhcWSClientConfig, StandaloneAhcWSClient }
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
    Metrics.start(jobComponents.actorSystem, jobComponents.config.scallopConf)

    Future {
      jobComponents.schedulerService.run()
    }(scala.concurrent.ExecutionContext.global)

    jobComponents.application
  }
}

class JobComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents with AssetsComponents {
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }
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
    jobsModule.actorsModule,
    defaultBodyParser)

  def schedulerService = jobsModule.schedulerModule.schedulerService

  lazy val wsClient: WSClient = {
    val parser = new WSConfigParser(configuration.underlying, environment.classLoader)
    val config = AhcWSClientConfig(wsClientConfig = parser.parse())
    val builder = new AhcConfigBuilder(config)
    val logging = new AsyncHttpClientConfig.AdditionalChannelInitializer() {
      override def initChannel(channel: play.shaded.ahc.io.netty.channel.Channel): Unit = {
        channel.pipeline.addFirst("log", new play.shaded.ahc.io.netty.handler.logging.LoggingHandler(classOf[WSClient]))
      }
    }
    val ahcBuilder = builder.configure()
    ahcBuilder.setHttpAdditionalChannelInitializer(logging)
    val ahcConfig = ahcBuilder.build()
    val asyncHttpClient = new DefaultAsyncHttpClient(ahcConfig)
    new AhcWSClient(new StandaloneAhcWSClient(asyncHttpClient)(jobsModule.actorsModule.materializer))
  }

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(
    new LeaderProxyFilter(wsClient, jobsModule.schedulerModule.electionService, config))

  override def router: Router = apiModule.router

  lazy val config = new MetronomeConfig(configuration)
}

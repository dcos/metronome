package dcos.metronome

import dcos.metronome.scheduler.SchedulerService
import dcos.metronome.scheduler.impl.SchedulerServiceImpl
import java.time.Clock

import controllers.AssetsComponents
import com.softwaremill.macwire._
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.api.v1.LeaderProxyFilter
import dcos.metronome.api.{ApiModule, ErrorHandler}
import mesosphere.marathon.MetricsModule
import mesosphere.marathon.core.async.ExecutionContexts
import mesosphere.marathon.core.base.{CrashStrategy, JvmExitsCrashStrategy}
import mesosphere.marathon.metrics.current.UnitOfMeasurement
import org.slf4j.LoggerFactory
import play.shaded.ahc.org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClient}
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient, AhcWSClientConfig, StandaloneAhcWSClient}
import play.api.libs.ws.{WSClient, WSConfigParser}
import play.api.mvc.EssentialFilter
import play.api.routing.Router

import scala.concurrent.Future
import scala.util.Failure

/**
  * Application loader that wires up the application dependencies using Macwire
  */
class JobApplicationLoader extends ApplicationLoader with StrictLogging {
  private[this] val log = LoggerFactory.getLogger(getClass)

  def load(context: Context): Application = try {
    val jobComponents = new JobComponents(context)

    jobComponents.metricsModule.start(jobComponents.actorSystem)

    val startedAt = System.currentTimeMillis()
    jobComponents.metricsModule.metrics.closureGauge(
      "uptime",
      () => (System.currentTimeMillis() - startedAt).toDouble / 1000.0, unit = UnitOfMeasurement.Time)

    Future {
      jobComponents.schedulerService.run()
    }(scala.concurrent.ExecutionContext.global).failed.foreach(e => {
      log.error("Error during application initialization. Shutting down.", e)
      JvmExitsCrashStrategy.crash(CrashStrategy.UncaughtException)
    })(ExecutionContexts.callerThread)

    jobComponents.application
  } catch {
    case ex: Throwable =>
      // something awful
      logger.error(s"Exception occurred while trying to initialize Metronome. Shutting down", ex)
      JvmExitsCrashStrategy.crash(CrashStrategy.UncaughtException)
      throw ex
  }
}

class JobComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents with AssetsComponents {
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }
  val clock: Clock = Clock.systemUTC()

  override lazy val httpErrorHandler = new ErrorHandler

  val config = new MetronomeConfig(configuration)

  val metricsModule = MetricsModule(config.scallopConf, configuration.underlying)

  private[this] val jobsModule: JobsModule = new JobsModule(config, actorSystem, clock, metricsModule)

  private[this] val apiModule: ApiModule = new ApiModule(controllerComponents, assets, httpErrorHandler, config,
    jobsModule.jobSpecModule.jobSpecService,
    jobsModule.jobRunModule.jobRunService,
    jobsModule.jobInfoModule.jobInfoService,
    jobsModule.pluginManger,
    jobsModule.queueModule.launchQueueService,
    jobsModule.actorsModule,
    metricsModule)

  val wsClient: WSClient = {
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

  override val httpFilters: Seq[EssentialFilter] = Seq(
    new LeaderProxyFilter(wsClient, jobsModule.schedulerModule.electionService, config))

  override def router: Router = apiModule.router

  /**
    * This needs to go last because calling `.coordinator` starts all the `startWhenLeader` hooks, and calling
    * startWhenLeader after .coordinator causes issues.
    */
  val schedulerService: SchedulerService = new SchedulerServiceImpl(
    jobsModule.schedulerRepositoriesModule.storageModule.persistenceStore,
    jobsModule.schedulerModule.leadershipModule.coordinator(),
    config,
    jobsModule.schedulerModule.electionModule.service,
    jobsModule.schedulerModule.prePostDriverCallbacks,
    jobsModule.schedulerModule.schedulerDriverFactory,
    jobsModule.schedulerRepositoriesModule.migration,
    jobsModule.schedulerModule.periodicOperations)

}

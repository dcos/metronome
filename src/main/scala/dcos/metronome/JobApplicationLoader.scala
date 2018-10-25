package dcos.metronome

import com.softwaremill.macwire._
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.LoggerFactory
import controllers.Assets
import dcos.metronome.api.v1.LeaderProxyFilter
import dcos.metronome.api.{ ApiModule, ErrorHandler }
import dcos.metronome.scheduler.SchedulerService
import dcos.metronome.scheduler.impl.SchedulerServiceImpl
import dcos.metronome.utils.CrashStrategy.UncaughtException
import dcos.metronome.utils.{ ExecutionContexts, JvmExitsCrashStrategy }
import dcos.metronome.utils.time.{ Clock, SystemClock }
import org.asynchttpclient.AsyncHttpClientConfig
import org.joda.time.DateTimeZone
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClient, AhcWSClientConfig }
import play.api.libs.ws.{ WSClient, WSConfigParser }
import play.api.mvc.EssentialFilter
import play.api.routing.Router

import scala.concurrent.Future
import scala.util.Failure

/**
  * Application loader that wires up the application dependencies using Macwire
  */
class JobApplicationLoader extends ApplicationLoader with StrictLogging {
  private[this] val log = LoggerFactory.getLogger(getClass)

  def load(context: Context): Application = {
    val jobComponents = new JobComponents(context)

    Future {
      jobComponents.schedulerService.run()
    }(scala.concurrent.ExecutionContext.global).onComplete {
      case Failure(e) =>
        log.error("Error during application initialization. Shutting down.", e)
        JvmExitsCrashStrategy.crash(UncaughtException)
    }(ExecutionContexts.callerThread)
    jobComponents.application
  }
}

class JobComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents {
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }
  lazy val assets: Assets = wire[Assets]

  lazy val clock: Clock = new SystemClock(DateTimeZone.UTC)

  override lazy val httpErrorHandler = new ErrorHandler

  private[this] val jobsModule: JobsModule = new JobsModule(config, actorSystem, clock)

  private[this] lazy val apiModule: ApiModule = new ApiModule(
    config,
    jobsModule.jobSpecModule.jobSpecService,
    jobsModule.jobRunModule.jobRunService,
    jobsModule.jobInfoModule.jobInfoService,
    jobsModule.pluginManger,
    httpErrorHandler,
    jobsModule.behaviorModule.metrics,
    assets,
    jobsModule.queueModule.launchQueueService)

  lazy val wsClient: WSClient = {
    val parser = new WSConfigParser(configuration, environment)
    val config = new AhcWSClientConfig(wsClientConfig = parser.parse())
    val builder = new AhcConfigBuilder(config)
    val logging = new AsyncHttpClientConfig.AdditionalChannelInitializer() {
      override def initChannel(channel: io.netty.channel.Channel): Unit = {
        channel.pipeline.addFirst("log", new io.netty.handler.logging.LoggingHandler(classOf[WSClient]))
      }
    }
    val ahcBuilder = builder.configure()
    ahcBuilder.setHttpAdditionalChannelInitializer(logging)
    val ahcConfig = ahcBuilder.build()
    new AhcWSClient(ahcConfig)
  }

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(
    new LeaderProxyFilter(wsClient, jobsModule.schedulerModule.electionService, config))

  override def router: Router = apiModule.router

  lazy val config = new MetronomeConfig(configuration)

  /**
    * This needs to go last because calling `.coordinator` starts all the `startWhenLeader` hooks, and calling
    * startWhenLeader after .coordinator causes issues.
    */
  val schedulerService: SchedulerService = new SchedulerServiceImpl(
    jobsModule.schedulerModule.leadershipModule.coordinator(),
    config,
    jobsModule.schedulerModule.electionModule.service,
    jobsModule.schedulerModule.prePostDriverCallbacks,
    jobsModule.schedulerModule.schedulerDriverFactory,
    jobsModule.metricsModule.metrics,
    jobsModule.schedulerRepositoriesModule.migration,
    jobsModule.schedulerModule.periodicOperations)

}

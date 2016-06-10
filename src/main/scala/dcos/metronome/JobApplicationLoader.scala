package dcos.metronome

import com.softwaremill.macwire._
import controllers.Assets
import dcos.metronome.scheduler.SchedulerModule
import dcos.metronome.api.{ ApiModule, ErrorHandler }
import dcos.metronome.utils.time.{ Clock, SystemClock }
import mesosphere.marathon.AllConf
import org.joda.time.DateTimeZone
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.routing.Router

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Application loader that wires up the application dependencies using Macwire
  */
class JobApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = {
    val jobComponents = new JobComponents(context)

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

  lazy val clock: Clock = new SystemClock(DateTimeZone.UTC)

  override lazy val httpErrorHandler = new ErrorHandler

  private[this] lazy val jobsModule: JobsModule = new JobsModule(
    config,
    actorSystem,
    clock
  )

  private[this] lazy val apiModule: ApiModule = new ApiModule(
    jobsModule.jobSpecModule.jobSpecService,
    jobsModule.jobRunModule.jobRunService,
    jobsModule.jobInfoModule.jobInfoService,
    jobsModule.loggingModule.loggingService,
    jobsModule.pluginManger,
    httpErrorHandler,
    assets
  )

  def schedulerService = jobsModule.schedulerModule.schedulerService

  override def router: Router = apiModule.router

  lazy val config = new Object with JobsConfig {

    lazy val master: String = "localhost:5050"
    lazy val pluginDir: Option[String] = configuration.getString("app.plugin.dir")
    lazy val pluginConf: Option[String] = configuration.getString("app.plugin.conf")
    lazy val runHistoryCount: Int = configuration.getInt("app.history.count").getOrElse(10)

    lazy val scallopConf: AllConf = {
      val options = Map[String, Option[String]](
        "--framework_name" -> Some("metronome"),
        "--master" -> Some(master),
        "--plugin_dir" -> pluginDir,
        "--plugin_conf" -> pluginConf,
        "--zk" -> Some("zk://localhost:2181/metronome")
      )
        .collect { case (name, Some(value)) => (name, value) }
        .flatMap { case (name, value) => Seq(name, value) }
      new AllConf(options.toSeq)
    }

    lazy val disableHttp: Boolean = scallopConf.disableHttp()
    lazy val httpPort: Int = scallopConf.httpPort()
    lazy val httpsPort: Int = scallopConf.httpsPort()
    lazy val hostname: String = scallopConf.hostname()
    lazy val zkTimeoutDuration: FiniteDuration = scallopConf.zkTimeoutDuration
    lazy val mesosLeaderUiUrl: Option[String] = scallopConf.mesosLeaderUiUrl.get
  }
}

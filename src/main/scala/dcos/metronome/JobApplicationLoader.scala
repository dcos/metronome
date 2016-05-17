package dcos.metronome

import com.softwaremill.macwire._
import controllers.Assets
import dcos.metronome.api.ApiModule
import dcos.metronome.greeting.GreetingConf
import dcos.metronome.ticking.{ BackPressure, TickingConf }
import mesosphere.marathon.AllConf
import org.joda.time.DateTime
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.libs.json.{ JsObject, Json }
import play.api.routing.Router

import scala.concurrent.duration._

/**
  * Application loader that wires up the application dependencies using Macwire
  */
class JobApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = new JobComponents(context).application
}

class JobComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents {
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }
  lazy val assets: Assets = wire[Assets]

  private[this] lazy val jobsModule: JobsModule = new JobsModule(config)

  private[this] lazy val apiModule: ApiModule = new ApiModule(
    jobsModule.greetingService,
    jobsModule.tickingService,
    jobsModule.pluginManger,
    httpErrorHandler,
    assets
  )

  override def router: Router = apiModule.router

  lazy val config = new Object with GreetingConf with TickingConf with JobsConfig {
    override lazy val greetingMessage: String = configuration.getString("test.foo").getOrElse("default")

    override val initialDelay: FiniteDuration = 1.second
    override val interval: FiniteDuration = 1.second
    override def tick(): Any = DateTime.now
    override val backPressure: BackPressure = new BackPressure {
      override val limit: Long = 100L
    }

    lazy val master: String = "localhost:5050"
    lazy val pluginDir: Option[String] = configuration.getString("app.plugin.dir")
    lazy val pluginConf: Option[String] = configuration.getString("app.plugin.conf")

    lazy val scallopConf: AllConf = {
      val options = Map[String, Option[String]](
        "--master" -> Some(master),
        "--plugin_dir" -> pluginDir,
        "--plugin_conf" -> pluginConf
      )
        .collect { case (name, Some(value)) => (name, value) }
        .flatMap { case (name, value) => Seq(name, value) }
      new AllConf(options.toSeq)
    }
  }
}

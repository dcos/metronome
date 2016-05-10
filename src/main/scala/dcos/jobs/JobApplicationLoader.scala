package dcos.jobs

import com.softwaremill.macwire._
import controllers.Assets
import dcos.jobs.api.ApiModule
import dcos.jobs.greeting.GreetingConf
import mesosphere.marathon.AllConf
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.api.routing.Router

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
    jobsModule.pluginManger,
    httpErrorHandler,
    assets
  )

  override def router: Router = apiModule.router

  lazy val config = new Object with GreetingConf with JobsConfig {
    override lazy val greetingMessage: String = configuration.getString("test.foo").getOrElse("default")

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

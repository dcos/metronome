package dcos.metronome

import com.softwaremill.macwire._
import controllers.Assets
import dcos.metronome.api.v1.LeaderProxyFilter
import dcos.metronome.api.{ ApiConfig, ApiModule, ErrorHandler }
import dcos.metronome.repository.impl.kv.ZkConfig
import dcos.metronome.utils.time.{ Clock, SystemClock }
import mesosphere.marathon.AllConf
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
import scala.concurrent.duration._
import scala.sys.SystemProperties

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

  private[this] lazy val jobsModule: JobsModule = wire[JobsModule]

  private[this] lazy val apiModule: ApiModule = new ApiModule(
    config,
    jobsModule.jobSpecModule.jobSpecService,
    jobsModule.jobRunModule.jobRunService,
    jobsModule.jobInfoModule.jobInfoService,
    jobsModule.pluginManger,
    httpErrorHandler,
    jobsModule.behaviorModule.metrics,
    assets
  )

  def schedulerService = jobsModule.schedulerModule.schedulerService

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
    new LeaderProxyFilter(wsClient, jobsModule.schedulerModule.electionService, config)
  )

  override def router: Router = apiModule.router

  lazy val config = new Object with JobsConfig with ApiConfig {
    import ConfigurationImplicits._

    lazy val frameworkName: String = configuration.getString("metronome.framework.name").getOrElse("metronome")
    lazy val mesosMaster: String = configuration.getString("metronome.mesos.master.url").getOrElse("localhost:5050")
    override lazy val mesosLeaderUiUrl: Option[String] = configuration.getString("metronome.mesos.leader.ui.url").orElse(scallopConf.mesosLeaderUiUrl.get)
    lazy val mesosRole: Option[String] = configuration.getString("metronome.mesos.role")
    lazy val mesosUser: Option[String] = configuration.getString("metronome.mesos.user").orElse(new SystemProperties().get("user.name"))
    lazy val mesosExecutorDefault: String = configuration.getString("metronome.mesos.executor.default").getOrElse("//cmd")
    lazy val mesosFailoverTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.mesos.failover.timeout").getOrElse(7.days)
    lazy val mesosAuthenticationPrincipal: Option[String] = configuration.getString("metronome.mesos.authentication.principal")
    lazy val mesosAuthenticationSecretsFile: Option[String] = configuration.getString("metronome.mesos.authentication.secret.file")

    lazy val enableFeatures: Option[String] = configuration.getString("metronome.features.enable")
    lazy val pluginDir: Option[String] = configuration.getString("metronome.plugin.dir")
    lazy val pluginConf: Option[String] = configuration.getString("metronome.plugin.conf")
    override lazy val runHistoryCount: Int = configuration.getInt("metronome.history.count").getOrElse(10)
    override lazy val withMetrics: Boolean = configuration.getBoolean("metronome.behavior.metrics").getOrElse(true)

    override lazy val disableHttp: Boolean = configuration.getBoolean("play.server.http.disableHttp").getOrElse(false)
    override lazy val httpPort: Int = configuration.getInt("play.server.http.port").getOrElse(9000)
    override lazy val httpsPort: Int = configuration.getInt("play.server.https.port").getOrElse(9443)

    override lazy val askTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.akka.ask.timeout").getOrElse(10.seconds)

    override lazy val zkURL: String = configuration.getString("metronome.zk.url").getOrElse(ZkConfig.DEFAULT_ZK_URL)
    override lazy val zkSessionTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.zk.session_timeout").getOrElse(ZkConfig.DEFAULT_ZK_SESSION_TIMEOUT)
    override lazy val zkTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.zk.timeout").getOrElse(ZkConfig.DEFAULT_ZK_TIMEOUT)
    override lazy val zkCompressionEnabled: Boolean = configuration.getBoolean("metronome.zk.compression.enabled").getOrElse(ZkConfig.DEFAULT_ZK_COMPRESSION_ENABLED)
    override lazy val zkCompressionThreshold: Long = configuration.getLong("metronome.zk.compression.threshold").getOrElse(ZkConfig.DEFAULT_ZK_COMPRESSION_THRESHOLD)

    override lazy val scallopConf: AllConf = {
      val flags = Seq[Option[String]](
        if (disableHttp) Some("--disable_http") else None,
        if (zkCompressionEnabled) Some("--zk_compression") else None
      )
      val options = Map[String, Option[String]](
        "--framework_name" -> Some(frameworkName),
        "--master" -> Some(mesosMaster),
        "--mesos_leader_ui_url" -> mesosLeaderUiUrl,
        "--plugin_dir" -> pluginDir,
        "--plugin_conf" -> pluginConf,
        "--http_port" -> Some(httpPort.toString),
        "--https_port" -> Some(httpsPort.toString),
        "--hostname" -> Some(hostname),
        "--zk" -> Some(zkURL),
        "--zk_session_timeout" -> Some(zkSessionTimeout.toMillis.toString),
        "--zk_timeout" -> Some(zkTimeout.toMillis.toString),
        "--zk_compression_threshold" -> Some(zkCompressionThreshold.toString),
        "--mesos_user" -> mesosUser,
        "--mesos_role" -> mesosRole,
        "--executor" -> Some(mesosExecutorDefault),
        "--enable_features" -> enableFeatures,
        "--failover_timeout" -> Some(mesosFailoverTimeout.toMillis.toString),
        "--leader_proxy_connection_timeout" -> Some(leaderProxyTimeout.toMillis.toString),
        "--task_launch_timeout" -> Some(taskLaunchTimeout.toMillis.toString),
        "--mesos_authentication_principal" -> mesosAuthenticationPrincipal,
        "--mesos_authentication_secret_file" -> mesosAuthenticationSecretsFile,
        "--env_vars_prefix" -> taskEnvVarsPrefix,
        "--on_elected_prepare_timeout" -> Some(leaderPreparationTimeout.toMillis.toString)
      )
        .collect { case (name, Some(value)) => (name, value) }
        .flatMap { case (name, value) => Seq(name, value) }
      new AllConf(options.toSeq ++ flags.flatten)
    }

    override lazy val reconciliationInterval: FiniteDuration = configuration.getFiniteDuration("metronome.scheduler.reconciliation.interval").getOrElse(15.minutes)
    override lazy val reconciliationTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.scheduler.reconciliation.timeout").getOrElse(1.minute)
    override lazy val enableStoreCache: Boolean = configuration.getBoolean("metronome.scheduler.store.cache").getOrElse(true)
    lazy val taskLaunchTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.scheduler.task.launch.timeout").getOrElse(5.minutes)
    lazy val taskEnvVarsPrefix: Option[String] = configuration.getString("metronome.scheduler.task.env.vars.prefix")

    override lazy val hostname: String = configuration.getString("metronome.leader.election.hostname").getOrElse(java.net.InetAddress.getLocalHost.getHostName)
    override lazy val leaderPreparationTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.leader.preparation.timeout").getOrElse(3.minutes)
    override lazy val leaderProxyTimeout: Duration = configuration.getFiniteDuration("metronome.leader.proxy.timeout").getOrElse(30.seconds)

    override lazy val maxActorStartupTime: FiniteDuration = configuration.getFiniteDuration("metronome.akka.actor.startup.max").getOrElse(10.seconds)

  }

}

object ConfigurationImplicits {
  implicit class Foo(val configuration: Configuration) extends AnyVal {
    def getFiniteDuration(path: String): Option[FiniteDuration] = configuration.getLong(path).map(_.milliseconds)
  }
}

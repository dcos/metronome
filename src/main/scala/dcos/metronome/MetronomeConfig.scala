package dcos.metronome

import dcos.metronome.api.ApiConfig
import dcos.metronome.repository.impl.kv.ZkConfig
import mesosphere.marathon.AllConf
import play.api.Configuration

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration._
import scala.sys.SystemProperties
import scala.util.Try

class MetronomeConfig(configuration: Configuration) extends JobsConfig with ApiConfig {
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

  lazy val hostname: String = configuration.getString("metronome.leader.election.hostname").getOrElse(java.net.InetAddress.getLocalHost.getHostName)
  lazy val httpPort: Option[Int] = configuration.getString("play.server.http.port").flatMap(intString => Try(intString.toInt).toOption)
  lazy val httpsPort: Int = configuration.getInt("play.server.https.port").getOrElse(9443)
  override lazy val hostnameWithPort: String = s"$hostname:${httpPort.getOrElse(httpsPort)}"

  override lazy val askTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.akka.ask.timeout").getOrElse(10.seconds)

  override lazy val zkURL: String = configuration.getString("metronome.zk.url").getOrElse(ZkConfig.DEFAULT_ZK_URL)
  override lazy val zkSessionTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.zk.session_timeout").getOrElse(ZkConfig.DEFAULT_ZK_SESSION_TIMEOUT)
  override lazy val zkTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.zk.timeout").getOrElse(ZkConfig.DEFAULT_ZK_TIMEOUT)
  override lazy val zkCompressionEnabled: Boolean = configuration.getBoolean("metronome.zk.compression.enabled").getOrElse(ZkConfig.DEFAULT_ZK_COMPRESSION_ENABLED)
  override lazy val zkCompressionThreshold: Long = configuration.getLong("metronome.zk.compression.threshold").getOrElse(ZkConfig.DEFAULT_ZK_COMPRESSION_THRESHOLD)

  override lazy val scallopConf: AllConf = {
  val flags = Seq[Option[String]](
  if (httpPort.isEmpty) Some("--disable_http") else None,
  if (zkCompressionEnabled) Some("--zk_compression") else None
  )
  val options = Map[String, Option[String]](
  "--framework_name" -> Some(frameworkName),
  "--master" -> Some(mesosMaster),
  "--mesos_leader_ui_url" -> mesosLeaderUiUrl,
  "--plugin_dir" -> pluginDir,
  "--plugin_conf" -> pluginConf,
  "--http_port" -> httpPort.map(_.toString),
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

  override lazy val leaderPreparationTimeout: FiniteDuration = configuration.getFiniteDuration("metronome.leader.preparation.timeout").getOrElse(3.minutes)
  override lazy val leaderProxyTimeout: Duration = configuration.getFiniteDuration("metronome.leader.proxy.timeout").getOrElse(30.seconds)

  override lazy val maxActorStartupTime: FiniteDuration = configuration.getFiniteDuration("metronome.akka.actor.startup.max").getOrElse(10.seconds)

}

private[this] object ConfigurationImplicits {
  implicit class LongToFiniteDuration(val configuration: Configuration) extends AnyVal {
    def getFiniteDuration(path: String): Option[FiniteDuration] = configuration.getLong(path).map(_.milliseconds)
  }
}

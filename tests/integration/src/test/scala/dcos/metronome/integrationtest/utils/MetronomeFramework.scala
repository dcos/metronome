package dcos.metronome.integrationtest.utils

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.UUID

import akka.Done
import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.stream.Materializer
import com.mesosphere.utils.{ PortAllocator, ProcessOutputToLogStream }
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.Seq
import mesosphere.marathon.Exception
import mesosphere.marathon.util.Retry
import org.apache.commons.io.FileUtils

import scala.async.Async.{ async, await }
import scala.collection.JavaConverters
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.Process
import scala.util.Try

import scala.concurrent.duration._

object MetronomeFramework {
  /**
    * Runs a metronome server for the given test suite
    * @param suiteName The test suite that owns this marathon
    * @param masterUrl The mesos master url
    * @param zkUrl The ZK url
    * @param conf any particular configuration
    * @param mainClass The main class
    */
  case class LocalMetronome(
    suiteName: String,
    masterUrl: String,
    zkUrl:     String,
    conf:      Map[String, String] = Map.empty,
    mainClass: String              = "play.core.server.ProdServerStart")(implicit
    val system: ActorSystem,
                                                                         val mat:       Materializer,
                                                                         val ctx:       ExecutionContext,
                                                                         val scheduler: Scheduler) extends MetronomeBase {

    // it'd be great to be able to execute in memory, but we can't due to GuiceFilter using a static :(
    val processBuilder = {
      val java = sys.props.get("java.home").fold("java")(_ + "/bin/java")
      val cp = sys.props.getOrElse("java.class.path", "target/classes")

      // Get JVM arguments, such as -javaagent:some.jar
      val runtimeMxBean = ManagementFactory.getRuntimeMXBean
      val runtimeArguments = JavaConverters.collectionAsScalaIterable(runtimeMxBean.getInputArguments).toSeq

      val cmd = Seq(java, "-Xmx1024m", "-Xms256m", "-XX:+UseConcMarkSweepGC", "-XX:ConcGCThreads=2") ++
        runtimeArguments ++ akkaJvmArgs ++
        Seq(s"-DmarathonUUID=$uuid -DtestSuite=$suiteName", s"-Dmetronome.zk.url=$zkUrl", s"-Dmetronome.mesos.master.url=$masterUrl", s"-Dmetronome.framework.name=metronome-$uuid", s"-Dplay.server.http.port=$httpPort", s"-Dplay.server.https.port=$httpsPort", "-classpath", cp, "-client", mainClass) // ++ args

      logger.info(s"Starting process in ${workDir}, Cmd is ${cmd}")

      Process(cmd, workDir, sys.env.toSeq: _*)
    }

  }

  trait MetronomeBase extends StrictLogging {

    val mainClass: String
    val suiteName: String
    val masterUrl: String
    val zkUrl: String
    val conf: Map[String, String]

    implicit val system: ActorSystem
    implicit val mat: Materializer
    implicit val ctx: ExecutionContext
    implicit val scheduler: Scheduler

    final val defaultRole = "foo"

    val uuid = UUID.randomUUID.toString

    lazy val httpPort = PortAllocator.ephemeralPort()
    lazy val httpsPort = PortAllocator.ephemeralPort()
    lazy val httpUrl = "http://localhost:" + httpPort

    val processBuilder: scala.sys.process.ProcessBuilder

    // lower the memory pressure by limiting threads.
    val akkaJvmArgs = Seq(
      "-Dakka.actor.default-dispatcher.fork-join-executor.parallelism-min=2",
      "-Dakka.actor.default-dispatcher.fork-join-executor.factor=1",
      "-Dakka.actor.default-dispatcher.fork-join-executor.parallelism-max=4",
      "-Dscala.concurrent.context.minThreads=2",
      "-Dscala.concurrent.context.maxThreads=32")

    val workDir = {
      val f = Files.createTempDirectory(s"metronome-$httpPort").toFile
      f.deleteOnExit()
      f
    }

    private def write(dir: File, fileName: String, content: String): String = {
      val file = File.createTempFile(fileName, "", dir)
      file.deleteOnExit()
      FileUtils.write(file, content, Charset.defaultCharset)
      file.setReadable(true)
      file.getAbsolutePath
    }

    val secretPath = write(workDir, fileName = "marathon-secret", content = "secret1")

    val mesosRole = conf.getOrElse("mesos_role", defaultRole)
    val config = Map(
      "master" -> masterUrl,
      "mesos_authentication_principal" -> "principal",
      "mesos_role" -> mesosRole,
      "http_port" -> httpPort.toString,
      "https_port" -> httpsPort.toString,
      "zk" -> zkUrl,
      "zk_timeout" -> 20.seconds.toMillis.toString,
      "zk_connection_timeout" -> 20.seconds.toMillis.toString,
      "zk_session_timeout" -> 20.seconds.toMillis.toString,
      "mesos_authentication_secret_file" -> s"$secretPath",
      "access_control_allow_origin" -> "*",
      "reconciliation_initial_delay" -> 5.minutes.toMillis.toString,
      "min_revive_offers_interval" -> "1000",
      "hostname" -> "localhost",
      "logging_level" -> "debug",
      "offer_matching_timeout" -> 10.seconds.toMillis.toString // see https://github.com/mesosphere/marathon/issues/4920
    ) ++ conf

    val args = config.flatMap {
      case (k, v) =>
        if (v.nonEmpty) {
          Seq(s"--$k", v)
        } else {
          Seq(s"--$k")
        }
    }(collection.breakOut)

    lazy val url = s"http://localhost:$httpPort"
    lazy val facade = new MetronomeFacade(url)

    def activePids: Seq[String] = {
      val PIDRE = """^\s*(\d+)\s+(\S*)\s*(.*)$""".r
      Process("jps -lv").!!.split("\n").collect {
        case PIDRE(pid, main, jvmArgs) if main.contains(mainClass) && jvmArgs.contains(uuid) => pid
      }(collection.breakOut)
    }

    @volatile var marathonProcess = Option.empty[Process]

    def create(): Process = {
      marathonProcess.getOrElse {
        val process = processBuilder.run(ProcessOutputToLogStream(s"$suiteName-LocalMetronome-$httpPort"))
        marathonProcess = Some(process)
        process
      }
    }

    def start(): Future[Done] = {
      create()

      val port = conf.get("http_port").orElse(conf.get("https_port")).map(_.toInt).getOrElse(httpPort)
      val future = Retry(s"Waiting for Metronome on $port", maxAttempts = Int.MaxValue, minDelay = 1.milli, maxDelay = 5.seconds, maxDuration = 4.minutes) {
        logger.info(s"Waiting for Metronome on port $port")
        async {
          val result = await(Http().singleRequest(Get(s"http://localhost:$port/leader")))
          logger.info(s"Response is: $result")
          result.discardEntityBytes() // forget about the body
          if (result.status.isSuccess()) { // linter:ignore //async/await
            logger.info("Metronome is reachable.")
            Done
          } else {
            throw new Exception(s"Metronome on port=$port hasn't started yet. Giving up waiting..")
          }
        }
      }
      future
    }

    def isRunning(): Boolean =
      activePids.nonEmpty

    def exitValue(): Option[Int] = marathonProcess.map(_.exitValue())

    def stop(): Future[Done] = {
      marathonProcess.fold(Future.successful(Done)){ p =>
        logger.info(s"Shutdown Metronome Framework ${suiteName}")
        p.destroy()
        p.exitValue()
        Future.successful(Done)
      }.andThen {
        case _ =>
          marathonProcess = Option.empty[Process]
      }
    }

    def restart(): Future[Done] = {
      logger.info(s"Restarting Metronome on $httpPort")
      async {
        await(stop())
        val x = await(start())
        logger.info(s"Restarted Metronome on $httpPort")
        x
      }
    }

    def close(): Unit = {
      stop().value
      Try(FileUtils.deleteDirectory(workDir))
    }
  }
}

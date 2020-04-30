package dcos.metronome.integrationtest

import java.io.File
import java.net.URL

import akka.actor.{ ActorSystem, Scheduler }
import akka.stream.Materializer
import com.mesosphere.utils.AkkaUnitTest
import com.mesosphere.utils.mesos.{ MesosAgentConfig, MesosClusterTest }
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.Seq
import dcos.metronome.integrationtest.utils.MetronomeFramework.MetronomeBase
import dcos.metronome.integrationtest.utils.{ MetronomeFacade, MetronomeFramework }
import mesosphere.marathon.SemVer
import mesosphere.marathon.io.IO
import org.apache.commons.io.FileUtils
import org.scalatest.Inside
import play.api.libs.json.{ JsArray, JsObject }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process.Process

/**
  * This integration test starts older Marathon versions one after another and finishes this upgrade procedure with the
  * current build. In each step we verify that all apps are still up and running.
  */
class UpgradeIntegrationTest extends AkkaUnitTest with MesosClusterTest with Inside with StrictLogging {

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  val zkURLBase = s"zk://${zkserver.connectUrl}/metronome-$suiteName"

  //  val marathonMinus3Artifact = MetronomeArtifact(SemVer(1, 6, 567, Some("2d8b3e438")))
  val metronomeMinus2Artifact = MetronomeArtifact(SemVer(0, 4, 4, None), "releases") // DC/OS 1.10
  val metronomeMinus1Artifact = MetronomeArtifact(SemVer(0, 6, 33, Some("b28106a"))) // DC/OS 1.13

  //   Configure Mesos to provide the Mesos containerizer with Docker image support.
  override lazy val agentConfig = MesosAgentConfig(
    launcher = "linux",
    isolation = Some("filesystem/linux,docker/runtime"),
    imageProviders = Some("docker"))

  override def beforeAll(): Unit = {
    logger.info("Download and extract older metronome versions")

    // Download Releases
    metronomeMinus1Artifact.downloadAndExtract()
    metronomeMinus2Artifact.downloadAndExtract()

    logger.info("Done with preparations")
    super.beforeAll()
  }

  case class MetronomeArtifact(version: SemVer, releasePath: String = "builds") {
    private val targetFolder: File = new File("target/universal").getAbsoluteFile
    val tarballName = s"metronome-${version}.tgz"
    val tarball = new File(targetFolder, tarballName)

    val downloadURL: URL = new URL(s"https://downloads.mesosphere.io/metronome/${releasePath}/${version}/metronome-${version}.tgz")

    val metronomeBaseFolder = new File(targetFolder, s"metronome-${version}")

    def downloadAndExtract() = {
      logger.info(s"Downloading $tarballName to ${tarball.getCanonicalPath}")
      if (!tarball.isFile) {
        targetFolder.mkdirs()
        FileUtils.copyURLToFile(downloadURL, tarball)
      }
      if (!metronomeBaseFolder.isDirectory) {
        targetFolder.mkdirs()
        IO.extractTGZip(tarball, targetFolder)
      }
    }
  }

  case class PackagedMetronome(metronomeBaseFolder: File, suiteName: String, masterUrl: String, zkUrl: String, conf: Map[String, String] = Map.empty)(
    implicit
    val system: ActorSystem, val mat: Materializer, val ctx: ExecutionContext, val scheduler: Scheduler) extends MetronomeBase {

    override val mainClass: String = "play.core.server.ProdServerStart"

    override val processBuilder = {
      val bin = new File(metronomeBaseFolder, "bin/metronome").getCanonicalPath

      val cmd = Seq("bash", bin, "-J-Xmx1024m", "-J-Xms256m", "-J-XX:+UseConcMarkSweepGC", "-J-XX:ConcGCThreads=2") ++ akkaJvmArgs ++
        Seq(s"-DmarathonUUID=$uuid -DtestSuite=$suiteName", s"-Dmetronome.zk.url=$zkUrl", s"-Dmetronome.mesos.master.url=$masterUrl", s"-Dmetronome.framework.name=metronome-$uuid", s"-Dplay.server.http.port=$httpPort", s"-Dplay.server.https.port=$httpsPort")

      logger.info(s"Starting process in ${workDir}, Cmd is ${cmd}")

      Process(cmd, workDir, sys.env.toSeq: _*)
    }
  }

  def versionWithoutCommit(version: SemVer): String = version.copy(commit = None).toString

  "A created job is persisted and survive update" in {
    When("The previous metronome is started")
    val zkUrl = s"$zkURLBase-upgrade-from-n-2"
    var metronomeFramework: MetronomeBase = PackagedMetronome(metronomeMinus1Artifact.metronomeBaseFolder, suiteName = s"$suiteName-n-minus-1", mesosMasterZkUrl, zkUrl)
    metronomeFramework.start().futureValue
    var metronomeUrl = "http://localhost:" + metronomeFramework.httpPort
    logger.info(s"Metronome n-2 started, reachable on: ${metronomeUrl}")

    Then("Metronome should be reachable")
    var metronome = new MetronomeFacade(metronomeUrl)
    metronome.info().value.status.intValue() shouldBe 200

    When("A job is created")
    val appId = "my-job"
    val jobDef =
      s"""
         |{
         |  "id": "${appId}",
         |  "description": "A job that sleeps",
         |  "run": {
         |    "cmd": "sleep 60",
         |    "cpus": 0.01,
         |    "mem": 32,
         |    "disk": 0
         |  }
         |}
      """.stripMargin

    val resp = metronome.createJob(jobDef)

    Then("The response should be OK")
    resp.value.status.intValue() shouldBe 201
    val createJobResp = metronome.getJob(appId)
    createJobResp.value.status.intValue() shouldBe 200
    logger.info("JobJson: " + createJobResp.entityPrettyJsonString)
    createJobResp.entityJson.as[JsArray].value.head.as[JsObject].value("id").as[String] shouldBe appId

    When("The current metronome is started")
    metronomeFramework.stop().futureValue

    metronomeFramework = MetronomeFramework.LocalMetronome(suiteName, mesosMasterZkUrl, zkUrl)
    metronomeFramework.start().futureValue

    metronomeUrl = "http://localhost:" + metronomeFramework.httpPort
    logger.info(s"Metronome dev started, reachable on: ${metronomeUrl}")

    metronome = new MetronomeFacade(metronomeUrl)

    Then("The Job should be available")
    val jobResp = metronome.getJob(appId)
    jobResp.value.status.intValue() shouldBe 200
    logger.info("JobJson: " + jobResp.entityPrettyJsonString)
    jobResp.entityJson.as[JsArray].value.head.as[JsObject].value("id").as[String] shouldBe appId

  }

}

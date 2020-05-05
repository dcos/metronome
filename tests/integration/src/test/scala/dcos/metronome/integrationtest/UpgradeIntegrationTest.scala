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
import play.api.libs.json._

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

  val metronomePre8746Regression = MetronomeArtifact(SemVer(0, 6, 33, Some("b28106a"))) // DC/OS 1.13.9
  val metronome8746Regression = MetronomeArtifact(SemVer(0, 6, 43, Some("4e1eac1"))) // DC/OS 2.0.1

  override def beforeAll(): Unit = {
    logger.info("Download and extract older metronome versions")

    // Download Releases
    metronomePre8746Regression.downloadAndExtract()
    metronome8746Regression.downloadAndExtract()

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

  def startMetronome(metronomeFramework: MetronomeBase): Unit = {
    metronomeFramework.start().futureValue
    val metronome = new MetronomeFacade(metronomeFramework.httpUrl)
    metronome.info().value.status.intValue() shouldBe 200
  }

  def startedMetronome(metronomeArtifact: MetronomeArtifact, zkUrl: String, name: String): PackagedMetronome = {
    val metronomeFramework = PackagedMetronome(metronomeArtifact.metronomeBaseFolder, suiteName = s"$suiteName-$name", mesosMasterZkUrl, zkUrl)
    startMetronome(metronomeFramework)
    logger.info(s"Metronome ${name} started, reachable on: ${metronomeFramework.httpUrl}")
    metronomeFramework
  }

  def createJobSuccessfully(metronome: MetronomeFacade, jobId: String, description: String): Unit = {
    val jobDef = Json.obj(
      "id" -> jobId,
      "description" -> description,
      "run" -> Json.obj(
        "cmd" -> "sleep 60",
        "cpus" -> 0.01,
        "mem" -> 32,
        "disk" -> 0))

    val resp = metronome.createJob(jobDef.toString())

    Then("The response should be OK")
    resp.value.status.intValue() shouldBe 201 withClue resp.entityPrettyJsonString

    val getJob = metronome.getJob(jobId)
    getJob.value.status.intValue() shouldBe 200
    logger.info("JobJson: " + getJob.entityPrettyJsonString)
    (getJob.entityJson \ "id").as[String] shouldBe jobId
  }

  "A created job is persisted and survives update" in {
    When("The previous metronome is started")

    val job1Id = "job-1"
    val job2Id = "job-2"
    val jobOriginalDescription = "original description"
    val jobUpdatedDescription = "updated description"
    val zkUrl = s"$zkURLBase-upgrade-around-8746"

    inside(startedMetronome(metronomePre8746Regression, zkUrl = zkUrl, name = "pre-8746")) {
      case metronomeFramework =>
        val metronome = new MetronomeFacade(metronomeFramework.httpUrl)

        When("Two jobs are created, job-1 and job-2")
        createJobSuccessfully(metronome, job1Id, jobOriginalDescription)
        createJobSuccessfully(metronome, job2Id, jobOriginalDescription)

        val createJobResp = metronome.getJob(job1Id)
        createJobResp.value.status.intValue() shouldBe 200
        logger.info("JobJson: " + createJobResp.entityPrettyJsonString)
        (createJobResp.entityJson \ "id").as[String] shouldBe job1Id

        metronomeFramework.stop().futureValue
    }

    And("A version with the MARATHON-8746 regression is started")
    inside(startedMetronome(metronome8746Regression, zkUrl = zkUrl, name = "with-8746")) {
      case metronomeFramework =>
        val metronome = new MetronomeFacade(metronomeFramework.httpUrl)

        And("A new version of job-2 is created")
        createJobSuccessfully(metronome, job2Id, jobUpdatedDescription)
        metronomeFramework.stop().futureValue
    }

    And(" current version of Metronome is started")
    inside(MetronomeFramework.LocalMetronome(suiteName, mesosMasterZkUrl, zkUrl)) {
      case metronomeFramework =>
        startMetronome(metronomeFramework)
        val metronome = new MetronomeFacade(metronomeFramework.httpUrl)
        Then("Metronome should have both jobs 1 and 2")

        val job1Response = metronome.getJob(job1Id)
        val job2Response = metronome.getJob(job2Id)
        logger.info(metronome.getJobs().entityPrettyJsonString)

        job1Response.success shouldBe true withClue job1Response.entityPrettyJsonString
        job2Response.success shouldBe true withClue job2Response.entityPrettyJsonString

        val job1Description = (job1Response.entityJson \ "description").as[String]
        val job2Description = (job2Response.entityJson \ "description").as[String]

        And("jobs 1 and 2 should be in the same state as created prior to the MARATHON-8746 version")
        job1Description shouldBe jobOriginalDescription
        job2Description shouldBe jobOriginalDescription
    }
  }
}

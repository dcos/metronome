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

  def versionWithoutCommit(version: SemVer): String =
    version.copy(commit = None).toString

  //  "n-1 Metronome version can be started" in {
  //    When("The previous metronome is started")
  //    val zkUrl = s"$zkURLBase-start-n-1"
  //    val prevMetronome = PackagedMetronome(metronomeMinus1Artifact.metronomeBaseFolder, suiteName = s"$suiteName-n-minus-1", mesosMasterZkUrl, zkUrl)
  //    prevMetronome.start().futureValue
  //
  //    Then("Metronome should be reachable")
  //    val metronomeUrl = "http://localhost:" + prevMetronome.httpPort
  //    logger.info(s"Metronome started, reachable on: ${metronomeUrl}")
  //
  //    val metronome = new MetronomeFacade(metronomeUrl)
  //    val resp = metronome.info()
  //
  //    val info = resp.entityPrettyJsonString
  //
  //    logger.info(s"InfoResult ${info}")
  //    resp.value.status.intValue() shouldBe 200
  //
  //    resp.entityJson.as[JsObject].value("version").as[String] shouldBe "0.6.33"
  //  }
  //
  //  "n-2 Metronome version can be started" in {
  //    When("The previous metronome is started")
  //    val zkUrl = s"$zkURLBase-start-n-2"
  //    val prevMetronome = PackagedMetronome(metronomeMinus2Artifact.metronomeBaseFolder, suiteName = s"$suiteName-n-minus-2", mesosMasterZkUrl, zkUrl)
  //    prevMetronome.start().futureValue
  //
  //    Then("Metronome should be reachable")
  //    val metronomeUrl = "http://localhost:" + prevMetronome.httpPort
  //    logger.info(s"Metronome started, reachable on: ${metronomeUrl}")
  //
  //    val metronome = new MetronomeFacade(metronomeUrl)
  //    val resp = metronome.info()
  //
  //    val info = resp.entityPrettyJsonString
  //
  //    logger.info(s"InfoResult ${info}")
  //    resp.value.status.intValue() shouldBe 200
  //
  //    // Metronome 0.4 doesn't have a version...
  //    resp.entityJson.as[JsObject].value("version").as[String] shouldBe "0.0.0"
  //    resp.entityJson.as[JsObject].value("libVersion").as[String] shouldBe "1.3.13"
  //  }

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

  //  "Ephemeral and persistent apps and pods" should {
  //    "survive an upgrade cycle" taggedAs WhenEnvSet(envVarRunMesosTests, default = "true") in {
  //
  //      val zkUrl = s"$zkURLBase-upgrade-cycle"
  //
  //      // Start apps in initial version
  //      Given(s"A Marathon n-3 is running (${marathonMinus3Artifact.version})")
  //      val marathonMinus3 = PackagedMarathon(marathonMinus3Artifact.marathonBaseFolder, suiteName = s"$suiteName-n-minus-3", mesosMasterZkUrl, zkUrl)
  //      marathonMinus3.start().futureValue
  //      (marathonMinus3.client.info.entityJson \ "version").as[String] should be(versionWithoutCommit(marathonMinus3Artifact.version))
  //
  //      And(s"new running apps in Marathon n-3 (${marathonMinus3Artifact.version})")
  //      val app_nm3_fail = appProxy(testBasePath / "app-nm3-fail", "v1", instances = 1, healthCheck = None)
  //      marathonMinus3.client.createAppV2(app_nm3_fail) should be(Created)
  //
  //      val app_nm3 = appProxy(testBasePath / "app-nm3", "v1", instances = 1, healthCheck = None)
  //      marathonMinus3.client.createAppV2(app_nm3) should be(Created)
  //
  //      patienceConfig
  //      eventually { marathonMinus3 should have (runningTasksFor(AbsolutePathId(app_nm3.id), 1)) }
  //      eventually { marathonMinus3 should have (runningTasksFor(AbsolutePathId(app_nm3_fail.id), 1)) }
  //
  //      val originalAppNm3Tasks: List[ITEnrichedTask] = marathonMinus3.client.tasks(AbsolutePathId(app_nm3.id)).value
  //      val originalAppNm3FailedTasks: List[ITEnrichedTask] = marathonMinus3.client.tasks(AbsolutePathId(app_nm3_fail.id)).value
  //
  //      When(s"Marathon n-3 is shut down (${marathonMinus3Artifact.version})")
  //      marathonMinus3.stop().futureValue
  //
  //      And(s"App ${app_nm3_fail.id} fails")
  //      AppMockFacade.suicideAll(originalAppNm3FailedTasks)
  //
  //      // Pass upgrade to n-2
  //      And(s"Marathon is upgraded to n-2 (${marathonMinus2Artifact.version})")
  //      val marathonMinus2 = PackagedMarathon(marathonMinus2Artifact.marathonBaseFolder, s"$suiteName-n-minus-2", mesosMasterZkUrl, zkUrl)
  //      marathonMinus2.start().futureValue
  //      (marathonMinus2.client.info.entityJson \ "version").as[String] should be(versionWithoutCommit(marathonMinus2Artifact.version))
  //
  //      And("new apps in Marathon n-2 are added")
  //      val app_nm2 = appProxy(testBasePath / "app-nm2", "v1", instances = 1, healthCheck = None)
  //      marathonMinus2.client.createAppV2(app_nm2) should be(Created)
  //
  //      val app_nm2_fail = appProxy(testBasePath / "app-nm2-fail", "v1", instances = 1, healthCheck = None)
  //      marathonMinus2.client.createAppV2(app_nm2_fail) should be(Created)
  //
  //      Then(s"All apps from ${marathonMinus2Artifact.version} are running")
  //      eventually { marathonMinus2 should have (runningTasksFor(AbsolutePathId(app_nm2.id), 1)) }
  //      eventually { marathonMinus2 should have (runningTasksFor(AbsolutePathId(app_nm2_fail.id), 1)) }
  //
  //      val originalAppNm2Tasks: List[ITEnrichedTask] = marathonMinus2.client.tasks(AbsolutePathId(app_nm2.id)).value
  //      val originalAppNm2FailedTasks: List[ITEnrichedTask] = marathonMinus2.client.tasks(AbsolutePathId(app_nm2_fail.id)).value
  //
  //      And(s"All apps from n-2 are still running (${marathonMinus2Artifact.version})")
  //      marathonMinus2.client.tasks(AbsolutePathId(app_nm3.id)).value should contain theSameElementsAs (originalAppNm3Tasks)
  //
  //      When(s"Marathon n-2 is shut down (${marathonMinus2Artifact.version})")
  //      marathonMinus2.stop().futureValue
  //
  //      And(s"App ${app_nm2_fail.id} fails")
  //      AppMockFacade.suicideAll(originalAppNm2FailedTasks)
  //
  //      And(s"Marathon is upgraded to n-1")
  //      val marathonMinus1 = PackagedMarathon(metronomeMinus1Artifact.metronomeBaseFolder, s"$suiteName-n-minus-1", mesosMasterZkUrl, zkUrl)
  //      marathonMinus1.start().futureValue
  //      (marathonMinus1.client.info.entityJson \ "version").as[String] should be(versionWithoutCommit(metronomeMinus1Artifact.version))
  //
  //      And(s"new pods in Marathon n-1 are added (${metronomeMinus1Artifact.version})")
  //      val resident_pod_nm1 = PodDefinition(
  //        id = testBasePath / "resident-pod-nm1",
  //        role = "foo",
  //        containers = Seq(
  //          MesosContainer(
  //            name = "task1",
  //            exec = Some(raml.MesosExec(raml.ShellCommand("cd $MESOS_SANDBOX && echo 'start' >> pst1/foo && python -m SimpleHTTPServer $ENDPOINT_TASK1"))),
  //            resources = raml.Resources(cpus = 0.1, mem = 32.0),
  //            endpoints = Seq(raml.Endpoint(name = "task1", hostPort = Some(0))),
  //            volumeMounts = Seq(VolumeMount(Some("pst"), "pst1", false))
  //          )
  //        ),
  //        volumes = Seq(PersistentVolume(name = Some("pst"), persistent = PersistentVolumeInfo(size = 10L))),
  //        networks = Seq(HostNetwork),
  //        instances = 1,
  //        unreachableStrategy = state.UnreachableDisabled,
  //        upgradeStrategy = state.UpgradeStrategy(0.0, 0.0)
  //      )
  //      marathonMinus1.client.createPodV2(resident_pod_nm1) should be(Created)
  //      val (resident_pod_nm1_port, resident_pod_nm1_address) = eventually {
  //        val status = marathonMinus1.client.status18(resident_pod_nm1.id)
  //        status.value.status shouldBe PodState.Stable
  //        status.value.instances(0).containers(0).endpoints(0).allocatedHostPort should be('defined)
  //        val port = status.value.instances(0).containers(0).endpoints(0).allocatedHostPort.get
  //        (port, status.value.instances(0).networks(0).addresses(0))
  //      }
  //
  //      Then(s"pod ${resident_pod_nm1.id} can be queried on http://$resident_pod_nm1_address:$resident_pod_nm1_port")
  //      implicit val requestTimeout = 30.seconds
  //      eventually { AkkaHttpResponse.request(Get(s"http://$resident_pod_nm1_address:$resident_pod_nm1_port/pst1/foo")).futureValue.entityString should be("start\n") }
  //
  //      Then(s"All apps from n-3 and n-2 are still running (${marathonMinus3Artifact.version} and ${marathonMinus2Artifact.version})")
  //      marathonMinus1.client.tasks(AbsolutePathId(app_nm3.id)).value should contain theSameElementsAs (originalAppNm3Tasks)
  //      marathonMinus1.client.tasks(AbsolutePathId(app_nm2.id)).value should contain theSameElementsAs (originalAppNm2Tasks)
  //
  //      // Pass upgrade to current
  //      When("Marathon is upgraded to the current version")
  //      marathonMinus1.stop().futureValue
  //      val marathonCurrent = LocalMarathon(suiteName = s"$suiteName-current", masterUrl = mesosMasterZkUrl, zkUrl = zkUrl)
  //      marathonCurrent.start().futureValue
  //      (marathonCurrent.client.info.entityJson \ "version").as[String] should be(BuildInfo.version.toString)
  //
  //      Then(s"All apps from n-3 and n-2 are still running (${marathonMinus3Artifact.version} and ${marathonMinus2Artifact.version})")
  //      val originalAppnm3TaskIds = originalAppNm3Tasks.map(_.id)
  //      val originalAppnm2TaskIds = originalAppNm2Tasks.map(_.id)
  //      marathonCurrent.client.tasks(AbsolutePathId(app_nm3.id)).value.map(_.id) should contain theSameElementsAs (originalAppnm3TaskIds)
  //      marathonCurrent.client.tasks(AbsolutePathId(app_nm2.id)).value.map(_.id) should contain theSameElementsAs (originalAppnm2TaskIds)
  //
  //      And(s"All apps from n-3 and n-2 are recovered and running again (${marathonMinus3Artifact.version} and ${marathonMinus2Artifact.version})")
  //      eventually { marathonCurrent should have(runningTasksFor(AbsolutePathId(app_nm3_fail.id), 1)) }
  //      marathonCurrent.client.tasks(AbsolutePathId(app_nm3_fail.id)).value should not contain theSameElementsAs(originalAppNm3FailedTasks)
  //
  //      eventually { marathonCurrent should have(runningTasksFor(AbsolutePathId(app_nm2_fail.id), 1)) }
  //      marathonCurrent.client.tasks(AbsolutePathId(app_nm2_fail.id)).value should not contain theSameElementsAs(originalAppNm2FailedTasks)
  //
  //      And(s"All pods from n-1 are still running (${metronomeMinus1Artifact.version})")
  //      eventually { marathonCurrent.client.status(resident_pod_nm1.id) should be(Stable) }
  //      eventually { AkkaHttpResponse.request(Get(s"http://$resident_pod_nm1_address:$resident_pod_nm1_port/pst1/foo")).futureValue.entityString should be("start\n") }
  //
  //      marathonCurrent.close()
  //    }
  //  }
  //
  //  "upgrade from n-3 directly to the latest" in {
  //    val zkUrl = s"$zkURLBase-to-latest"
  //    val marathonNm3 = PackagedMarathon(marathonMinus3Artifact.marathonBaseFolder, suiteName = s"$suiteName-n-minus-3", mesosMasterZkUrl, zkUrl)
  //
  //    // Start apps in n-3
  //    Given(s"A Marathon n-3 is running (${marathonMinus3Artifact.version})")
  //    marathonNm3.start().futureValue
  //    (marathonNm3.client.info.entityJson \ "version").as[String] should be(versionWithoutCommit(marathonMinus3Artifact.version))
  //
  //    And("new running apps in Marathon n-3")
  //    val app_nm3_fail = appProxy(testBasePath / "app-nm3-fail", "v1", instances = 1, healthCheck = None)
  //    marathonNm3.client.createAppV2(app_nm3_fail) should be(Created)
  //
  //    val app_nm3 = appProxy(testBasePath / "prod" / "db" / "app-nm3", "v1", instances = 1, healthCheck = None)
  //    marathonNm3.client.createAppV2(app_nm3) should be(Created)
  //
  //    patienceConfig
  //    eventually { marathonNm3 should have (runningTasksFor(AbsolutePathId(app_nm3.id), 1)) }
  //    eventually { marathonNm3 should have (runningTasksFor(AbsolutePathId(app_nm3_fail.id), 1)) }
  //
  //    val originalAppNm3Tasks: List[ITEnrichedTask] = marathonNm3.client.tasks(AbsolutePathId(app_nm3.id)).value
  //    val originalAppNm3FailedTasks = marathonNm3.client.tasks(AbsolutePathId(app_nm3_fail.id)).value
  //
  //    When("Marathon n-3 is shut down")
  //    marathonNm3.stop().futureValue
  //
  //    AppMockFacade.suicideAll(originalAppNm3FailedTasks)
  //
  //    // Pass upgrade to current
  //    When("Marathon is upgraded to the current version")
  //    val marathonCurrent = LocalMarathon(suiteName = s"$suiteName-current", masterUrl = mesosMasterZkUrl, zkUrl = zkUrl)
  //    marathonCurrent.start().futureValue
  //    (marathonCurrent.client.info.entityJson \ "version").as[String] should be(BuildInfo.version.toString)
  //
  //    Then("All apps from n-3 are still running")
  //    val originalTaskIds = originalAppNm3Tasks.map(_.id)
  //    marathonCurrent.client.tasks(AbsolutePathId(app_nm3.id)).value.map(_.id) should contain theSameElementsAs (originalTaskIds)
  //
  //    And("All apps from n-3 are recovered and running again")
  //    eventually { marathonCurrent should have(runningTasksFor(AbsolutePathId(app_nm3_fail.id), 1)) }
  //
  //    marathonCurrent.close()
  //  }
  //
  //  "resident app can be restarted after upgrade from n-1" in {
  //    val zkUrl = s"$zkURLBase-resident-apps"
  //    val marathonnm1 = PackagedMarathon(metronomeMinus1Artifact.metronomeBaseFolder, suiteName = s"$suiteName-n-minus-1", mesosMasterZkUrl, zkUrl)
  //
  //    Given(s"A Marathon n-1 is running (${metronomeMinus1Artifact.version})")
  //    marathonnm1.start().futureValue
  //    (marathonnm1.client.info.entityJson \ "version").as[String] should be(versionWithoutCommit(metronomeMinus1Artifact.version))
  //
  //    And("new running apps in Marathon n-1")
  //    val containerPath = "persistent-volume"
  //    val residentApp_nm1 = residentApp(
  //      id = testBasePath / "resident-app-nm1",
  //      containerPath = containerPath,
  //      cmd = s"""echo "data" >> $containerPath/data && sleep 1000""")
  //    marathonnm1.client.createAppV2(residentApp_nm1) should be(Created)
  //
  //    patienceConfig
  //    eventually { marathonnm1 should have (runningTasksFor(AbsolutePathId(residentApp_nm1.id), 1)) }
  //    val originalAppnm1Tasks = marathonnm1.client.tasks(AbsolutePathId(residentApp_nm1.id)).value
  //
  //    When("We restart the app")
  //    marathonnm1.client.restartApp(AbsolutePathId(residentApp_nm1.id)) should be(OK)
  //
  //    Then("We have new running tasks")
  //    eventually {
  //      marathonnm1.client.tasks(AbsolutePathId(residentApp_nm1.id)).value should not contain theSameElementsAs(originalAppnm1Tasks)
  //      marathonnm1 should have (runningTasksFor(AbsolutePathId(residentApp_nm1.id), 1))
  //    }
  //
  //    // Pass upgrade to current
  //    When("Marathon is upgraded to the current version")
  //    marathonnm1.stop().futureValue
  //    val marathonCurrent = LocalMarathon(suiteName = s"$suiteName-current", masterUrl = mesosMasterZkUrl, zkUrl = zkUrl)
  //    marathonCurrent.start().futureValue
  //    (marathonCurrent.client.info.entityJson \ "version").as[String] should be(BuildInfo.version.toString)
  //
  //    Then(s"All apps from n-1 are still running (${metronomeMinus1Artifact.version}")
  //    marathonCurrent should have (runningTasksFor(AbsolutePathId(residentApp_nm1.id), 1))
  //    val restartedAppnm1Tasks = marathonCurrent.client.tasks(AbsolutePathId(residentApp_nm1.id)).value
  //
  //    When("We restart the app again")
  //    marathonCurrent.client.restartApp(AbsolutePathId(residentApp_nm1.id)) should be(OK)
  //
  //    Then("We have new running tasks")
  //    eventually {
  //      marathonCurrent.client.tasks(AbsolutePathId(residentApp_nm1.id)).value should not contain theSameElementsAs(restartedAppnm1Tasks)
  //      marathonCurrent should have (runningTasksFor(AbsolutePathId(residentApp_nm1.id), 1))
  //    }
  //
  //    marathonCurrent.close()
  //  }
  //
  //  /**
  //    * Scala [[HavePropertyMatcher]] that checks that numberOfTasks are in running state for app appId on given Marathon.
  //    *
  //    * Do not use the class directly but [[UpgradeIntegrationTest.runningTasksFor]]:
  //    *
  //    * {{{
  //    *   marathon17 should have(runningTasksFor(app_nm2.id.toPath, 2))
  //    * }}}
  //    *
  //    * @param appId The app the is checked for running tasks.
  //    * @param numberOfTasks The number of tasks that should be running.
  //    */
  //  class RunningTasksMatcher(appId: AbsolutePathId, numberOfTasks: Int) extends HavePropertyMatcher[BaseMarathon, List[ITEnrichedTask]] {
  //    def apply(marathon: BaseMarathon): HavePropertyMatchResult[List[ITEnrichedTask]] = {
  //      val tasks = marathon.client.tasks(appId).value
  //      val notRunningTasks = tasks.filter(_.state != "TASK_RUNNING")
  //      val matches = tasks.size == numberOfTasks && notRunningTasks.size == 0
  //      HavePropertyMatchResult(matches, "runningTasks", List.empty, notRunningTasks)
  //    }
  //  }
  //
  //  def runningTasksFor(appId: AbsolutePathId, numberOfTasks: Int) = new RunningTasksMatcher(appId, numberOfTasks)
  //
  //  override val testBasePath = AbsolutePathId("/")
  //  override val healthCheckPort: Int = 0
}

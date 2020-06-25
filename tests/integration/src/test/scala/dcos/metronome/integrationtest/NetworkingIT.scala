package dcos.metronome.integrationtest

import com.mesosphere.utils.mesos.MesosAgentConfig
import org.scalatest.Inside
import play.api.libs.json.{ JsArray, JsObject, Json }

import scala.concurrent.duration._

class NetworkingIT extends MetronomeITBase with Inside {

  override lazy val agentConfig = MesosAgentConfig(containerizers = "docker")

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  implicit class FixtureExtensions(f: Fixture) {
    def createJob(jobJson: String): Unit = {
      val hostResponse = (f.metronome.createJob(jobJson))
      Then("The response should be OK")
      hostResponse shouldBe Created
    }

    def runJob(jobId: String): Unit = {
      val startRunResp = f.metronome.startRun(jobId)
      Then("The response should be OK")
      startRunResp shouldBe Created
    }

    def waitForActive(jobId: String): Unit = {
      eventually(timeout(30.seconds)) {
        val runsJson = f.metronome.getRuns(jobId)
        runsJson shouldBe OK
        val runs = runsJson.entityJson.as[JsArray]
        runs.value should have size 1

        val run = runs.value.head.as[JsObject]
        val status = run.value("status").as[String]
        status shouldBe "ACTIVE"
      }
    }
  }

  "A job run should complete" in withFixture() { f =>
    Given("Two jobs, a bridge network job and a host network job")
    val hostJobId = "my-job"
    val hostNetworkJob =
      s"""
         |{
         |  "id": "${hostJobId}",
         |  "description": "A job that sleeps",
         |  "run": {
         |    "cmd": "sleep 60",
         |    "docker": {
         |      "image": "alpine"
         |    },
         |    "networks": [{"mode": "host"}],
         |    "cpus": 0.01,
         |    "mem": 32,
         |    "disk": 0
         |  }
         |}
      """.stripMargin

    val bridgeJobId = "bridge-job"
    val bridgeNetworkJob =
      s"""
         |{
         |  "id": "${bridgeJobId}",
         |  "description": "A job that sleeps",
         |  "run": {
         |    "cmd": "sleep 60",
         |    "docker": {
         |      "image": "alpine"
         |    },
         |    "networks": [{"mode": "container/bridge"}],
         |    "cpus": 0.01,
         |    "mem": 32,
         |    "disk": 0
         |  }
         |}
      """.stripMargin

    When("I create the jobs")
    f.createJob(hostNetworkJob)
    f.createJob(bridgeNetworkJob)
    And("I run the jobs")
    f.runJob(hostJobId)
    f.runJob(bridgeJobId)

    Then("the jobs should become active")
    f.waitForActive(hostJobId)
    f.waitForActive(bridgeJobId)

    And("the jobs should be launched with the respective networks")
    val json = Json.parse(mesosFacade.state().entityString)
    val tasksJson = (json \ "frameworks" \ 0 \ "tasks").as[Seq[JsObject]]

    val networks = tasksJson.map { taskJson =>
      (taskJson \ "container" \ "docker" \ "network").as[String]
    }.sorted

    networks shouldBe Seq("BRIDGE", "HOST")
  }
}

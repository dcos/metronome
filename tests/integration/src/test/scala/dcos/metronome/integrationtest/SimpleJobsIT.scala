package dcos.metronome.integrationtest

import play.api.libs.json.JsArray

import scala.concurrent.duration._

class SimpleJobsIT extends MetronomeIT {

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  "A job run should complete" in withFixture() { f =>
    When("A job description is posted")
    val jobDef =
      """
        |{
        |  "id": "my-job",
        |  "description": "A job that sleeps",
        |  "run": {
        |    "cmd": "sleep 60",
        |    "cpus": 0.01,
        |    "mem": 32,
        |    "disk": 0
        |  }
        |}
      """.stripMargin

    val resp = f.metronome.createJob(jobDef)

    Then("The response should be OK")
    resp.value.status.intValue() shouldBe 201

    When("A job run is started")
    val startRunResp = f.metronome.startRun("my-job")

    Then("The response should be OK")
    startRunResp.value.status.intValue() shouldBe 201

    When("The runs endpoint is queried")
    val runsJson = f.metronome.getRuns("my-job")

    Then("The runs list should have a size of 1")
    val runs = runsJson.entityJson.as[JsArray]
    runs.value.length shouldBe 1
  }

}

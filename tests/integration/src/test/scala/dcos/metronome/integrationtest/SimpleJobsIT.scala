package dcos.metronome.integrationtest

import org.scalatest.time.{ Minutes, Span }
import play.api.libs.json.{ JsArray, JsObject }

import scala.concurrent.duration._

class SimpleJobsIT extends MetronomeITBase {

  override val timeLimit = Span(3, Minutes)
  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  "A job run should complete" in withFixture() { f =>
    When("A job description is posted")
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

    val resp = f.metronome.createJob(jobDef)

    Then("The response should be OK")
    resp.value.status.intValue() shouldBe 201

    When("A job run is started")
    val startRunResp = f.metronome.startRun(appId)

    Then("The response should be OK")
    startRunResp.value.status.intValue() shouldBe 201

    eventually(timeout(30.seconds)) {
      val runsJson = f.metronome.getRuns(appId)
      runsJson.value.status.intValue() shouldBe 200
      val runs = runsJson.entityJson.as[JsArray]
      runs.value.length shouldBe 1

      val run = runs.value.head.as[JsObject]
      val status = run.value("status").as[String]
      status shouldBe "ACTIVE"
    }

  }

  "RestartPolicy should work" in withFixture() { f =>
    When("A job description is posted")
    val appId = "restart-policy"
    val jobDef =
      s"""
         |{
         |  "id": "${appId}",
         |  "description": "A job that fails repeatedly and has a max restart time",
         |  "run": {
         |    "cmd": "exit 1",
         |    "cpus": 0.01,
         |    "mem": 32,
         |    "disk": 0,
         |    "restart": {
         |      "activeDeadlineSeconds": 30,
         |      "policy": "ON_FAILURE"
         |    }
         |  }
         |}
      """.stripMargin

    val resp = f.metronome.createJob(jobDef)

    Then("The response should be OK")
    logger.info("Resp: " + resp.entityString)
    resp.value.status.intValue() shouldBe 201

    When("A job run is started")
    val startRunResp = f.metronome.startRun(appId)

    Then("The response should be OK")
    startRunResp.value.status.intValue() shouldBe 201

    Then("Eventually, the run should reach state ABORTED")
    eventually(timeout(60.seconds)) {
      val jobWithHistory = f.metronome.getJobWithHistory(appId)
      jobWithHistory.value.status.intValue() shouldBe 200
      logger.info("JobWithHistory: " + jobWithHistory.entityPrettyJsonString)

      val history = jobWithHistory.entityJson.as[JsObject].value("history").as[JsObject].value
      history("failureCount").as[Int] shouldBe 1
      history("successCount").as[Int] shouldBe 0
    }
  }

}

package dcos.metronome.integrationtest

import org.scalatest.time.{ Minutes, Span }
import play.api.libs.json.{ JsArray, JsObject }

import scala.concurrent.Await
import scala.concurrent.duration._

class PersistenceIT extends MetronomeITBase {

  override val timeLimit = Span(3, Minutes)
  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  "A job and run should be available after a restart of metronome" in withFixture() { f =>
    When("A job description is posted")
    val jobId = "persistence-my-job"
    val jobDef =
      s"""
        |{
        |  "id": "$jobId",
        |  "description": "A job that sleeps",
        |  "run": {
        |    "cmd": "sleep 120",
        |    "cpus": 0.02,
        |    "mem": 64,
        |    "disk": 0
        |  }
        |}
      """.stripMargin

    val resp = f.metronome.createJob(jobDef)

    Then("The response should be OK")
    resp shouldBe Created

    When("A job run is started")
    val startRunResp = f.metronome.startRun(jobId)

    Then("The response should be OK")
    startRunResp shouldBe Created

    eventually(timeout(30.seconds)) {
      val runsJson = f.metronome.getRuns(jobId)
      runsJson shouldBe OK
      val runs = runsJson.entityJson.as[JsArray]
      runs.value should have size 1

      val run = runs.value.head.as[JsObject]
      val status = run.value("status").as[String]
      status shouldBe "ACTIVE"
    }

    When("Metronome is stopped and restarted")
    Await.result(f.metronomeFramework.stop(), 30.seconds)
    Await.result(f.metronomeFramework.start(), 60.seconds)

    Then("The Job and the Run should be available")
    val jobResp = f.metronome.getJob(jobId)
    jobResp shouldBe OK
    (jobResp.entityJson \ "id").as[String] shouldBe jobId

    val runResp = f.metronome.getRuns(jobId)
    val runs = runResp.entityJson.as[JsArray]
    runs.value.length shouldBe 1
  }

}

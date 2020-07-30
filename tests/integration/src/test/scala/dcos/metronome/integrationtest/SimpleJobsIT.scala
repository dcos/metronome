package dcos.metronome.integrationtest

import play.api.libs.json.{JsArray, JsObject}

import scala.concurrent.duration._

class SimpleJobsIT extends MetronomeITBase {

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  "A job run should complete" in withFixture() { f =>
    When("A job description is posted")
    val appId = "my-job"
    val jobDef =
      s"""
        |{
        |  "id": "$appId",
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
    resp shouldBe Created

    When("A job run is started")
    val startRunResp = f.metronome.startRun(appId)

    Then("The response should be OK")
    startRunResp shouldBe Created

    eventually(timeout(30.seconds)) {
      val runsJson = f.metronome.getRuns(appId)
      runsJson shouldBe OK
      val runs = runsJson.entityJson.as[JsArray]
      runs.value should have size 1

      val run = runs.value.head.as[JsObject]
      val status = run.value("status").as[String]
      status shouldBe "ACTIVE"
    }
  }

  "Job B with dependency on job A should complete" in withFixture() { f =>
    Given("Jobs A and B")
    val jobA = "my-job-a"
    val jobADef =
      s"""
         |{
         |  "id": "$jobA",
         |  "description": "A job that sleeps",
         |  "run": {
         |    "cmd": "sleep 5",
         |    "cpus": 0.01,
         |    "mem": 32,
         |    "disk": 0
         |  }
         |}
      """.stripMargin
    f.metronome.createJob(jobADef) should be(Created)

    val jobB = "my-job-b"
    val jobBDef =
      s"""
         |{
         |  "id": "$jobB",
         |  "description": "A job that sleeps",
         |  "dependencies": [{"id": "$jobA"}],
         |  "run": {
         |    "cmd": "sleep 60",
         |    "cpus": 0.01,
         |    "mem": 32,
         |    "disk": 0
         |  }
         |}
      """.stripMargin
    f.metronome.createJob(jobBDef) should be(Created)

    When("Job A is run")
    f.metronome.startRun(jobA) should be(Created)

    Then("Job B should be triggered")
    eventually(timeout(30.seconds)) {
      val runsJson = f.metronome.getRuns(jobB)
      runsJson shouldBe OK
      val runs = runsJson.entityJson.as[JsArray]
      runs.value should have size 1

      val run = runs.value.head.as[JsObject]
      val status = run.value("status").as[String]
      status shouldBe "ACTIVE"
    }
  }

}

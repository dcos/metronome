package dcos.metronome.integrationtest

import com.mesosphere.utils.mesos.MesosFacade.ITFramework

class BasicTestsIT extends MetronomeITBase {

  "Metronome should start" in withFixture() { f =>
    Then("once example framework is connected, Mesos should return it's framework Id")
    val frameworks: Seq[ITFramework] = mesosFacade.frameworks().value.frameworks

    frameworks.size should be(1)

    val exampleFramework: ITFramework = frameworks.head

    logger.info("FrameworkInfo: " + exampleFramework)

    val info = f.metronome.info.entityPrettyJsonString

    logger.info(s"InfoResult ${info}")
  }

  "Create a job should be possible" in withFixture() { f =>
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

    When("The job spec is requested again")
    val jobJson = f.metronome.getJob("my-job")

    Then("The job should be returned")
    val job = jobJson.entityJson(0)

    (job \ "id").as[String] shouldBe "my-job"
    (job \ "run" \ "mem").as[Int] shouldBe 32
  }

}

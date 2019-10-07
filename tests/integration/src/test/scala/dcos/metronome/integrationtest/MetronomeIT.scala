package dcos.metronome.integrationtest

import com.mesosphere.utils.AkkaUnitTest
import com.mesosphere.utils.mesos.MesosClusterTest
import com.mesosphere.utils.mesos.MesosFacade.ITFramework
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.integrationtest.utils.{ MetronomeFacade, MetronomeFramework }
import org.apache.mesos.v1.Protos.FrameworkID
import org.scalatest.Inside

import scala.concurrent.duration._

class MetronomeIT extends AkkaUnitTest with MesosClusterTest with Inside with StrictLogging {

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

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

  def withFixture(frameworkId: Option[FrameworkID.Builder] = None)(fn: Fixture => Unit): Unit = {
    val f = new Fixture(frameworkId)
    try fn(f)
    finally {
      f.metronomeFramework.stop()
    }
  }

  class Fixture(existingFrameworkId: Option[FrameworkID.Builder] = None) extends StrictLogging {
    logger.info("Create Fixture with new Metronome...")

    val zkUrl = s"zk://${zkserver.connectUrl}/metronome"
    val masterUrl = mesosFacade.url.getHost + ":" + mesosFacade.url.getPort

    val metronomeFramework = new MetronomeFramework.LocalMetronome("xxx", masterUrl, zkUrl)

    logger.info("Starting metronome...")
    metronomeFramework.start().futureValue

    val metronomeUrl = "http://localhost:" + metronomeFramework.httpPort
    logger.info(s"Metronome started, reachable on: ${metronomeUrl}")

    val metronome = new MetronomeFacade(metronomeUrl)
  }

}

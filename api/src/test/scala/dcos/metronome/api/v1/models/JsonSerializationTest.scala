package dcos.metronome
package api.v1.models

import com.mesosphere.utils.UnitTest
import dcos.metronome.jobinfo.JobInfo
import dcos.metronome.model.{JobId, JobSpec, Network}
import play.api.libs.json._

class JsonSerializationTest extends UnitTest {
  "network serialization" should {
    "serialize back and forth" in {
      val network = Network(name = Some("user"), mode = Network.NetworkMode.Container, labels = Map("a" -> "b"))
      Json.toJson(network).as[Network] shouldBe network
    }

    "drops empty name and labels from the serialized json" in {
      val network = Network(name = None, mode = Network.NetworkMode.Host, labels = Map.empty)
      Json.toJson(network) shouldBe Json.obj("mode" -> "host")
    }

    "jobSpec formatting" should {
      val jobSpec = JobSpec(id = JobId("b"), dependencies = Seq(JobId("a")))

      "parse job dependencies properly back and forth" in {
        val jobJson = Json.toJson(jobSpec)
        (jobJson \ "dependencies").get shouldBe Json.arr(Json.obj("id" -> "a"))
        val reparsed = jobJson.as[JobSpec]
        reparsed.dependencies shouldBe Seq(JobId("a"))
      }

      "parse a serialized JobInfo" in {
        val jobInfo = JobInfo(jobSpec, Nil, None, None, None)
        val jobInfoJson = Json.toJson(jobInfo)
        val reparsed = jobInfoJson.as[JobSpec]
        reparsed.dependencies shouldBe Seq(JobId("a"))
      }

    }
  }
}

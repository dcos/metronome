package dcos.metronome
package api.v1.models

import com.mesosphere.utils.UnitTest
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

    "it parses job dependencies properly back and forth" in {
      val jobSpec = JobSpec(id = JobId("a"), dependencies = Seq(JobId("b")))
      val reparsed = Json.toJson(jobSpec).as[JobSpec]
      reparsed.dependencies shouldBe Seq(JobId("b"))
    }
  }
}

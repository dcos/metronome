package dcos.metronome.api.v1

import com.eclipsesource.schema.SchemaValidator
import com.mesosphere.utils.UnitTest
import dcos.metronome.api.v1.models.schema
import org.scalatest.{FunSuite, Matchers}
import dcos.metronome.utils.test.Mockito
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json._

class JobSpecJsonSchemaTest extends UnitTest with Mockito with Matchers with TableDrivenPropertyChecks {
  val runTemplate = Json.obj(
    "cpus" -> 1,
    "disk" -> 0,
    "mem" -> 32)

  val jobSpecIds = Table(
    ("id", "valid"),
    ("a", true),
    ("a-a", true),
    ("a-a.a-a", true),
    ("a.a.a", true),
    ("a1a.a1.a1", true),
    ("a1234567890-a1234567890.a1234567890-a1234567890.a", true),
    ("a-", false),
    ("a.", false),
    ("a1.a-", false),
    ("a1.a1.", false),
    ("-a", false),
    (".a", false),
    ("-a1.a", false),
    ("a1-.a", false),
    (".a1.a1", false),

    // invalid characters
    ("a?.a", false),
    ("a?a.a", false),
    ("a.a?", false),
    ("a.a?a", false),
  )

  val schemaValidator = SchemaValidator()

  def checkJob[T](job: JsObject)(body: JsResult[JsObject] => T) = {
    val result = schemaValidator.validate(schema.JobSpecSchema.schemaType, job)
    withClue(result.toString) {
      body(result)
    }
  }

  "ids are validated" in {
    forAll(jobSpecIds) { (id, shouldSucceed) =>
      val updated = Json.obj("id" -> id, "run" -> runTemplate)
      checkJob(updated) { result =>
        result.isSuccess shouldBe shouldSucceed
      }
    }
  }

  "network validation" should {
    "accept host networks" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse("""{"networks": [{"mode": "host"}]}""").as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe true
      }
    }

    "accept container/bridge networks with labels" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse("""{"networks": [{"mode": "container/bridge", "labels": {"a": "b"}}]}""").as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe true
      }
    }

    "accept container networks with name and labels" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse(
          """{
            |  "networks": [{
            |    "name": "user",
            |    "mode": "container",
            |    "labels": {"a":"b"}
            |  }]
            |}""".stripMargin).as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe true
      }
    }

    "reject invalid modes" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse(
          """{
            |  "networks": [{
            |    "name": "user",
            |    "mode": "invalid",
            |    "labels": {"a":"b"}
            |  }]
            |}""".stripMargin).as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe false
      }
    }

    "reject invalid fields" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse(
          """{
            |  "networks": [{
            |    "invalid": "field",
            |    "mode": "host"
            |  }]
            |}""".stripMargin).as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe false
      }
    }

    "require mode to be specified" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse(
          """{
            |  "networks": [{
            |    "name": "user"
            |  }]
            |}""".stripMargin).as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe false
      }
    }

    "require labels to be strings" in {
      val updated = Json.obj("id" -> "job",
        "run" -> (runTemplate ++ Json.parse(
          """{
            |  "networks": [{
            |    "labels": {"a": []}
            |  }]
            |}""".stripMargin).as[JsObject]))

      checkJob(updated) { result =>
        result.isSuccess shouldBe false
      }
    }
  }
}

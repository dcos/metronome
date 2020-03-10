package dcos.metronome.api.v1

import com.eclipsesource.schema.SchemaValidator
import dcos.metronome.api.v1.models.schema
import org.scalatest.{ FunSuite, Matchers }
import dcos.metronome.utils.test.Mockito
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json._

class JobSpecJsonSchemaTest extends FunSuite with Mockito with Matchers with TableDrivenPropertyChecks {
  val template = Json.obj(
    "id" -> "test",
    "run" -> Json.obj(
      "cpus" -> 1,
      "disk" -> 0,
      "mem" -> 32))

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
      (".a1.a1", false))

  val schemaValidator = SchemaValidator()

  test(s"id should be validated") {
    forAll(jobSpecIds) { (id, shouldSucceed) =>
      val updated = template + ("id", JsString(id))
      val result = schemaValidator.validate(schema.JobSpecSchema.schemaType, updated)
      println(result)
      withClue(result.toString) {
        result.isSuccess shouldBe (shouldSucceed)
      }
    }
  }
}

package dcos.metronome
package repository.impl.kv.marshaller

import java.time.{ LocalDateTime, ZoneOffset }

import dcos.metronome.model._
import org.scalatest.{ FunSuite, Matchers }

import scala.concurrent.duration._

class JobRunMarshallerTest extends FunSuite with Matchers {
  test("round-trip of a JobRun") {
    val f = new Fixture
    JobRunMarshaller.fromBytes(JobRunMarshaller.toBytes(f.jobRun)) should be(Some(f.jobRun))
  }

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobRunMarshaller.fromBytes(invalidBytes) should be(None)
  }

  class Fixture {
    val jobSpec = JobSpec(
      JobId("job.id"))

    val jobRun = JobRun(
      JobRunId(jobSpec.id, "run.id"),
      jobSpec,
      JobRunStatus.Active,
      LocalDateTime.parse("2004-09-06T08:50:12.000").toInstant(ZoneOffset.UTC),
      Some(LocalDateTime.parse("2004-09-06T08:50:12.000").toInstant(ZoneOffset.UTC)),
      Some(1 minute),
      Map.empty)
  }
}

package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.model._
import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.{ FunSuite, Matchers }

class JobStatusMarshallerTest extends FunSuite with Matchers {
  test("round-trip of a JobStatus") {
    val f = new Fixture
    JobStatusMarshaller.fromBytes(JobStatusMarshaller.toBytes(f.jobStatus)) should be (Some(f.jobStatus))
  }

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobStatusMarshaller.fromBytes(invalidBytes) should be (None)
  }

  class Fixture {
    val jobStatus = JobStatus(
      PathId("/my/wonderful/job"),
      successCount = 1337,
      failureCount = 31337,
      lastSuccessAt = Some(DateTime.parse("1984-09-06T08:50:12.000Z")),
      lastFailureAt = Some(DateTime.now(DateTimeZone.UTC)),
      activeRuns = Seq.empty
    )
  }
}

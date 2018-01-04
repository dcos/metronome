package dcos.metronome
package repository.impl.kv.marshaller

import dcos.metronome.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.{ FunSuite, Matchers }

class JobHistoryMarshallerTest extends FunSuite with Matchers {
  test("round-trip of a JobHistory") {
    val f = new Fixture
    JobHistoryMarshaller.fromBytes(JobHistoryMarshaller.toBytes(f.jobHistory)) should be (Some(f.jobHistory))
  }

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobHistoryMarshaller.fromBytes(invalidBytes) should be (None)
  }

  class Fixture {
    val successfulJobRunInfo = JobRunInfo(
      JobRunId(JobId("/test"), "successful"),
      DateTime.parse("2004-09-06T08:50:12.000Z"),
      DateTime.parse("2014-09-06T08:50:12.000Z")
    )

    val finishedJobRunInfo = JobRunInfo(
      JobRunId(JobId("/test"), "finished"),
      DateTime.parse("1984-09-06T08:50:12.000Z"),
      DateTime.parse("1994-09-06T08:50:12.000Z")
    )

    val jobHistory = JobHistory(
      JobId("/my/wonderful/job"),
      successCount = 1337,
      failureCount = 31337,
      lastSuccessAt = Some(DateTime.parse("2014-09-06T08:50:12.000Z")),
      lastFailureAt = Some(DateTime.now(DateTimeZone.UTC)),
      successfulRuns = Seq(successfulJobRunInfo),
      failedRuns = Seq(finishedJobRunInfo)
    )
  }
}

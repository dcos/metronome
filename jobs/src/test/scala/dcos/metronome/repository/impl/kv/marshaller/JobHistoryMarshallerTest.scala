package dcos.metronome
package repository.impl.kv.marshaller

import java.time.{ Clock, LocalDateTime, ZoneOffset }

import dcos.metronome.model._
import org.scalatest.{ FunSuite, Matchers }

class JobHistoryMarshallerTest extends FunSuite with Matchers {
  test("round-trip of a JobHistory") {
    val f = new Fixture
    JobHistoryMarshaller.fromBytes(JobHistoryMarshaller.toBytes(f.jobHistory)) should be (Some(f.jobHistory))
  }

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobHistoryMarshaller.fromBytes(invalidBytes.to[IndexedSeq]) should be (None)
  }

  class Fixture {
    val successfulJobRunInfo = JobRunInfo(
      JobRunId(JobId("/test"), "successful"),
      LocalDateTime.parse("2004-09-06T08:50:12.000").toInstant(ZoneOffset.UTC),
      LocalDateTime.parse("2014-09-06T08:50:12.000").toInstant(ZoneOffset.UTC))

    val finishedJobRunInfo = JobRunInfo(
      JobRunId(JobId("/test"), "finished"),
      LocalDateTime.parse("1984-09-06T08:50:12.000").toInstant(ZoneOffset.UTC),
      LocalDateTime.parse("1994-09-06T08:50:12.000").toInstant(ZoneOffset.UTC))

    val jobHistory = JobHistory(
      JobId("/my/wonderful/job"),
      successCount = 1337,
      failureCount = 31337,
      lastSuccessAt = Some(LocalDateTime.parse("2014-09-06T08:50:12.000").toInstant(ZoneOffset.UTC)),
      lastFailureAt = Some(Clock.systemUTC().instant()),
      successfulRuns = Seq(successfulJobRunInfo),
      failedRuns = Seq(finishedJobRunInfo))
  }
}

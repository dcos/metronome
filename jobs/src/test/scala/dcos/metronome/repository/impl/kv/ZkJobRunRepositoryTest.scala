package dcos.metronome
package repository.impl.kv

import dcos.metronome.model.{ JobId, JobRunId }
import dcos.metronome.utils.state.PersistentStoreWithNestedPathsSupport
import dcos.metronome.utils.test.Mockito
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.concurrent.ScalaFutures._

import scala.concurrent.Future

class ZkJobRunRepositoryTest extends FunSuite with Mockito with Matchers {

  test("ids") {
    val f = new Fixture

    f.store.allIds("job-runs").returns(
      Future.successful(Seq("first.job", "second.job")))

    f.store.allIds("job-runs/first.job").returns(
      Future.successful(Seq("first.run", "second.run")))

    f.store.allIds("job-runs/second.job").returns(
      Future.successful(Seq("third.run", "fourth.run")))

    f.repository.ids().futureValue should contain theSameElementsAs (
      Seq(
        JobRunId(JobId("first.job"), "first.run"),
        JobRunId(JobId("first.job"), "second.run"),
        JobRunId(JobId("second.job"), "third.run"),
        JobRunId(JobId("second.job"), "fourth.run")))
  }

  class Fixture {
    val ec = scala.concurrent.ExecutionContext.global

    val store: PersistentStoreWithNestedPathsSupport = mock[PersistentStoreWithNestedPathsSupport]
    val repository = new ZkJobRunRepository(store, ec)
  }
}

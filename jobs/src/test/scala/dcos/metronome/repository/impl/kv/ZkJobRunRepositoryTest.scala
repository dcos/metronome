package dcos.metronome.repository.impl.kv

import dcos.metronome.model.JobRunId
import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.state.PathId
import mesosphere.util.state.PersistentStoreWithNestedPathsSupport
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.concurrent.ScalaFutures._

import scala.concurrent.Future

class ZkJobRunRepositoryTest extends FunSuite with Mockito with Matchers {

  test("ids") {
    val f = new Fixture

    f.store.allIds("job-runs").returns(
      Future.successful(Seq("first.job", "second.job"))
    )

    f.store.allIds("job-runs/first.job").returns(
      Future.successful(Seq("first.run", "second.run"))
    )

    f.store.allIds("job-runs/second.job").returns(
      Future.successful(Seq("third.run", "fourth.run"))
    )

    f.repository.ids().futureValue should contain theSameElementsAs (
      Seq(
        JobRunId(PathId("/first/job"), "first.run"),
        JobRunId(PathId("/first/job"), "second.run"),
        JobRunId(PathId("/second/job"), "third.run"),
        JobRunId(PathId("/second/job"), "fourth.run")
      )
    )
  }

  class Fixture {
    val ec = scala.concurrent.ExecutionContext.global

    val store: PersistentStoreWithNestedPathsSupport = mock[PersistentStoreWithNestedPathsSupport]
    val repository = new ZkJobRunRepository(store, ec)
  }
}

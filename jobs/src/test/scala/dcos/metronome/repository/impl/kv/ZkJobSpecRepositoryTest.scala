package dcos.metronome.repository.impl.kv

import dcos.metronome.model.JobSpec
import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.state.PathId
import mesosphere.util.state.PersistentStoreWithNestedPathsSupport
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures

import concurrent.Future

class ZkJobSpecRepositoryTest extends FunSuite with Mockito with ScalaFutures {

  test("delete") {
    val f = new Fixture
    f.store.delete(any).returns(Future.successful(true))

    f.repository.delete(f.pathId).futureValue

    verify(f.store).delete("job-runs/foo.bar")
    verify(f.store).delete("job-specs/foo.bar")
  }

  test("create") {
    val f = new Fixture

    f.store.createPath(any).returns(Future.successful(Unit))

    f.repository.create(f.pathId, f.jobSpec).failed.futureValue

    verify(f.store).createPath("job-runs/foo.bar")
    verify(f.store).create(eq("job-specs/foo.bar"), any)
  }

  class Fixture {
    val ec = scala.concurrent.ExecutionContext.global

    val store: PersistentStoreWithNestedPathsSupport = mock[PersistentStoreWithNestedPathsSupport]
    val repository = new ZkJobSpecRepository(store, ec)

    val pathId = PathId("/foo/bar")
    val jobSpec = JobSpec(pathId)
  }
}
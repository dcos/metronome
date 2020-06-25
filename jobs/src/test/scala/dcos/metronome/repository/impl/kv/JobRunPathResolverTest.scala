package dcos.metronome
package repository.impl.kv

import dcos.metronome.model.{JobId, JobRunId}
import org.scalatest.{FunSuite, Matchers}

class JobRunPathResolverTest extends FunSuite with Matchers {

  test("test JobRunPathResolver#fromPath") {
    val f = new Fixture
    f.resolver.fromPath(f.path) should be(f.id)
  }

  test("test JobRunPathResolver#basePath") {
    val f = new Fixture
    f.resolver.basePath should be(f.basePath)
  }

  test("test JobRunPathResolver#toPath(JobRunId)") {
    val f = new Fixture
    f.resolver.toPath(f.id) should be(s"""${f.basePath}/${f.path}""")
  }

  test("test JobRunPathResolver#toPath(JobId)") {
    val f = new Fixture
    f.resolver.toPath(f.jobId) should be(s"""${f.basePath}/${f.parentPath}""")
  }

  class Fixture {
    val basePath = "job-runs"
    val jobId = JobId("foo.bar.baz")
    val runId = "run"
    val id = JobRunId(jobId, runId)
    val parentPath = "foo.bar.baz"
    val path = s"""$parentPath/$runId"""
    val resolver = JobRunPathResolver
  }
}

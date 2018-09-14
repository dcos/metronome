package dcos.metronome
package repository.impl.kv

import dcos.metronome.model.JobId
import org.scalatest.{ FunSuite, Matchers }

class JobIdPathResolverTest extends FunSuite with Matchers {

  test("test JobIdPathResolverTest#FromPath") {
    val f = new Fixture
    f.resolver.fromPath(f.path) should be (f.jobId)
  }

  test("test JobIdPathResolverTest#toPath") {
    val f = new Fixture
    f.resolver.toPath(f.jobId) should be (s"""${f.basePath}/${f.path}""")
  }

  class Fixture {
    val basePath = "base"
    val resolver = JobIdPathResolver(basePath)
    val path = "foo.bar.faz"
    val jobId = JobId(path)
  }
}

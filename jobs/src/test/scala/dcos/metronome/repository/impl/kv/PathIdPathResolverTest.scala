package dcos.metronome.repository.impl.kv

import mesosphere.marathon.state.PathId
import org.scalatest.{ FunSuite, Matchers }

class PathIdPathResolverTest extends FunSuite with Matchers {

  test("test PathIdPathResolverTest#FromPath") {
    val f = new Fixture
    f.resolver.fromPath(f.path) should be (f.pathId)
  }

  test("test PathIdPathResolverTest#toPath") {
    val f = new Fixture
    f.resolver.toPath(f.pathId) should be (s"""${f.basePath}/${f.path}""")
  }

  class Fixture {
    val basePath = "base"
    val resolver = PathIdPathResolver(basePath)
    val pathId = PathId("/foo/bar/faz")
    val path = "foo.bar.faz"
  }
}

package dcos.metronome

import org.scalatest.{ Matchers, WordSpec }

class MarathonBuildInfoTest extends WordSpec with Matchers {

  "BuildInfo" should {
    "return a default version" in {
      // metronome should never depend on snapshot version of marathon
      //MarathonBuildInfo.version.toString().contains("SNAPSHOT") should be(false)
      // TODO: Comment in again after we have merged the code in marathon
      true
    }
  }
}
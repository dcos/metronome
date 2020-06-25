package dcos.metronome
package model

import com.mesosphere.utils.UnitTest
import dcos.metronome.ValidationTestLike

class NetworkValidationTest extends UnitTest with ValidationTestLike {
  import Builders._
  import com.wix.accord.validate
  "host networking validation" should {
    "require that labels and name are not set" in {
      validate(
        newNetwork(mode = Network.NetworkMode.Host, name = Some("name"), labels = Map("key" -> "value"))
      ) should haveViolations(
        "/name" -> "must be empty for host network",
        "/labels" -> "must be empty for host network"
      )
    }
  }

  "bridge networking validation" should {
    "require that name is not set" in {
      validate(newNetwork(mode = Network.NetworkMode.ContainerBridge, name = Some("name"))) should haveViolations(
        "/name" -> "must be empty for container/bridge networks"
      )
    }

    "allow specifications of label" in {
      validate(newNetwork(mode = Network.NetworkMode.ContainerBridge, labels = Map("key" -> "value"))) should be(
        aSuccess
      )
    }
  }

  "container networking validation" should {
    "require that name is set and is not empty" in {
      validate(newNetwork(mode = Network.NetworkMode.Container, name = None)) should haveViolations(
        "/name" -> "must not be empty for container networks"
      )
      validate(newNetwork(mode = Network.NetworkMode.Container, name = Some(""))) should haveViolations(
        "/name" -> "must not be empty for container networks"
      )
    }

    "should allow the specification of labels" in {
      validate(
        newNetwork(mode = Network.NetworkMode.Container, name = Some("labels"), labels = Map("key" -> "value"))
      ) should be(aSuccess)
    }
  }

}

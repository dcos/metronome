package dcos.metronome.model

import dcos.metronome.Seq

case class Network(name: Option[String], mode: Network.NetworkMode, labels: Map[String, String]) {}

object Network {

  import com.wix.accord.dsl._
  import dcos.metronome.model.ValidationHelpers._

  sealed trait NetworkMode {
    val name: String
  }

  object NetworkMode {

    case object Host extends NetworkMode {
      val name = "host"
    }

    case object ContainerBridge extends NetworkMode {
      val name = "container/bridge"
    }

    case object Container extends NetworkMode {
      val name = "container"
    }

    def all = Seq(Host, ContainerBridge, Container)

    val byName = all.iterator.map { m => m.name -> m }.toMap
  }

  import NetworkValidationMessages._
  implicit val validNetworkDefinition = validator[Network] { network =>
    if (network.mode == NetworkMode.Host) {
      network.name is withHint(HostNetworkHint, empty)
      network.labels is withHint(HostNetworkHint, empty)
    } else if (network.mode == NetworkMode.ContainerBridge) {
      network.name is withHint(BridgeNetworkHint, empty)
    } else if (network.mode == NetworkMode.Container) {
      network.name is withHint(ContainerNetworkHint, notEmpty)
      network.name.each is withHint(ContainerNetworkHint, notEmpty)
    }
  }
}

object NetworkValidationMessages {
  val HostNetworkHint = "for host network"
  val BridgeNetworkHint = "for container/bridge networks"
  val ContainerNetworkHint = "for container networks"
}

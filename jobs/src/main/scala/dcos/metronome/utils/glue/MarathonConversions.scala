package dcos.metronome.utils.glue

import dcos.metronome.model.{ EnvVarSecret, EnvVarValue, EnvVarValueOrSecret, Network, SecretDef }
import mesosphere.marathon
import mesosphere.marathon.plugin.NetworkSpec
import mesosphere.marathon.core.pod

object MarathonConversions {

  def envVarToMarathon(envVars: Map[String, EnvVarValueOrSecret]): Map[String, marathon.state.EnvVarValue] = {
    envVars.mapValues {
      case EnvVarValue(value)   => marathon.state.EnvVarString(value)
      case EnvVarSecret(secret) => marathon.state.EnvVarSecretRef(secret)
    }
  }

  def secretsToMarathon(secrets: Map[String, SecretDef]): Map[String, marathon.state.Secret] = {
    secrets.map { case (name, value) => name -> marathon.state.Secret(value.source) }
  }

  def networkToMarathon(network: Network): NetworkSpec = {
    network match {
      case Network(Some(name), Network.NetworkMode.Container, labels) =>
        pod.ContainerNetwork(name = name, labels = labels)
      case Network(None, Network.NetworkMode.ContainerBridge, labels) =>
        pod.BridgeNetwork(labels = labels)
      case Network(None, Network.NetworkMode.Host, _) =>
        pod.HostNetwork
      case o =>
        throw new IllegalStateException(s"Network ${o} is illegal and should have been prevented by validation")
    }
  }

}

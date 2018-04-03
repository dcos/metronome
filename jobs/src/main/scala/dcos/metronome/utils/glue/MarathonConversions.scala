package dcos.metronome.utils.glue

import dcos.metronome.model.{ EnvVarSecret, EnvVarValue, EnvVarValueOrSecret, SecretDef }
import mesosphere.marathon

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

}

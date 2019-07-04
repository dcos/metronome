package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model.{ EnvVarSecret, EnvVarValue, EnvVarValueOrSecret, SecretDef }

import scala.collection.mutable

object EnvironmentMarshaller {
  implicit class EnvironmentToProto(val environment: Map[String, EnvVarValueOrSecret]) extends AnyVal {
    def toEnvProto: Iterable[Protos.EnvironmentVariable] = environment.collect {
      case (key, EnvVarValue(value)) =>
        Protos.EnvironmentVariable.newBuilder()
          .setKey(key)
          .setValue(value)
          .build
    }
    def toEnvSecretProto: Iterable[Protos.JobSpec.RunSpec.EnvironmentVariableSecret] = environment.collect {
      case (name, EnvVarSecret(secretId)) =>
        Protos.JobSpec.RunSpec.EnvironmentVariableSecret.newBuilder()
          .setName(name)
          .setSecretId(secretId)
          .build
    }
  }

  implicit class SecretsToProto(val secrets: Map[String, SecretDef]) extends AnyVal {
    def toProto: Iterable[Protos.JobSpec.RunSpec.Secret] = secrets.map {
      case (secretId, SecretDef(source)) =>
        Protos.JobSpec.RunSpec.Secret.newBuilder()
          .setId(secretId)
          .setSource(source)
          .build()
    }
  }

  implicit class ProtosToEnvironment(val environmentVariables: mutable.Buffer[Protos.EnvironmentVariable]) extends AnyVal {
    def toModel: Map[String, EnvVarValueOrSecret] = environmentVariables.map { environmentVariable =>
      environmentVariable.getKey -> EnvVarValue(environmentVariable.getValue)
    }.toMap
  }

  implicit class ProtosToEnvironmentSecrets(val environmentSecrets: mutable.Buffer[Protos.JobSpec.RunSpec.EnvironmentVariableSecret]) extends AnyVal {
    def toModel: Map[String, EnvVarValueOrSecret] = environmentSecrets.map { environmentSecret =>
      environmentSecret.getName -> EnvVarSecret(environmentSecret.getSecretId)
    }.toMap
  }

  implicit class ProtosToSecrets(val secrets: mutable.Buffer[Protos.JobSpec.RunSpec.Secret]) extends AnyVal {
    def toModel: Map[String, SecretDef] = secrets.map { secret =>
      secret.getId -> SecretDef(secret.getSource)
    }.toMap
  }
}

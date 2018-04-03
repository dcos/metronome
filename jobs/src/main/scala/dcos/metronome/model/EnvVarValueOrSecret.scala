package dcos.metronome.model

trait EnvVarValueOrSecret

case class EnvVarValue(value: String) extends EnvVarValueOrSecret

object EnvVarValue {
  implicit object playJsonFormat extends play.api.libs.json.Format[EnvVarValue] {
    def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[EnvVarValue] = {
      json.validate[String].map(EnvVarValue.apply)
    }
    def writes(envVarValue: EnvVarValue): play.api.libs.json.JsValue = {
      play.api.libs.json.JsString(envVarValue.value)
    }
  }
}

/**
  * An environment variable set to a secret
  * @param secret The name of the secret to refer to. At runtime, the value of the
  *   secret will be injected into the value of the variable.
  */
case class EnvVarSecret(secret: String) extends EnvVarValueOrSecret

object EnvVarSecret {
  implicit val playJsonFormat = play.api.libs.json.Json.format[EnvVarSecret]
}

object EnvVarValueOrSecret {
  implicit object playJsonFormat extends play.api.libs.json.Format[EnvVarValueOrSecret] {
    def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[EnvVarValueOrSecret] = {
      json.validate[EnvVarValue].orElse(json.validate[EnvVarSecret])
    }
    def writes(envOrSecret: EnvVarValueOrSecret): play.api.libs.json.JsValue = {
      envOrSecret match {
        case envVarValue: EnvVarValue   => play.api.libs.json.Json.toJson(envVarValue)(EnvVarValue.playJsonFormat)
        case envVarSecret: EnvVarSecret => play.api.libs.json.Json.toJson(envVarSecret)(EnvVarSecret.playJsonFormat)
      }
    }
  }
}
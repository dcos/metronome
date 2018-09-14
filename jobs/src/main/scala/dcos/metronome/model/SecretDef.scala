package dcos.metronome.model

/**
  * A secret declaration
  * @param source reference to a secret which will be injected with a value from the secret store.
  */
case class SecretDef(source: String)

object SecretDef {
  import play.api.libs.json.Reads._
  implicit object playJsonFormat extends play.api.libs.json.Format[SecretDef] {
    def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[SecretDef] = {
      val source = json.\("source").validate[String](play.api.libs.json.JsPath.read[String](minLength[String](ConstraintSourceMinLength)))
      val _errors = Seq(("source", source)).collect({
        case (field, e: play.api.libs.json.JsError) => e.repath(play.api.libs.json.JsPath.\(field)).asInstanceOf[play.api.libs.json.JsError]
      })
      if (_errors.nonEmpty) _errors.reduceOption[play.api.libs.json.JsError](_.++(_)).getOrElse(_errors.head)
      else play.api.libs.json.JsSuccess(SecretDef(source = source.get))
    }
    def writes(secret: SecretDef): play.api.libs.json.JsValue = {
      val source = play.api.libs.json.Json.toJson(secret.source)
      play.api.libs.json.JsObject(Seq(("source", source)).filter(_._2 != play.api.libs.json.JsNull).++(Seq.empty))
    }
  }
  val ConstraintSourceMinLength = 1
}
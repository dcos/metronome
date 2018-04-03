package dcos.metronome.model

/**
  * A secret declaration
  * @param source The source of the secrets value.
  *   The format dependes on the secret store
  *   minLength: 1
  */
case class SecretDef(source: String)

object SecretDef {
  import play.api.libs.json.Reads._
  implicit object playJsonFormat extends play.api.libs.json.Format[SecretDef] {
    def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[SecretDef] = {
      val source = json.\("source").validate[String](play.api.libs.json.JsPath.read[String](minLength[String](1)))
      val _errors = Seq(("source", source)).collect({
        case (field, e: play.api.libs.json.JsError) => e.repath(play.api.libs.json.JsPath.\(field)).asInstanceOf[play.api.libs.json.JsError]
      })
      if (_errors.nonEmpty) _errors.reduceOption[play.api.libs.json.JsError](_.++(_)).getOrElse(_errors.head)
      else play.api.libs.json.JsSuccess(SecretDef(source = source.get))
    }
    def writes(o: SecretDef): play.api.libs.json.JsValue = {
      val source = play.api.libs.json.Json.toJson(o.source)
      play.api.libs.json.JsObject(Seq(("source", source)).filter(_._2 != play.api.libs.json.JsNull).++(Seq.empty))
    }
  }
  val ConstraintSourceMinlength = 1
}
package dcos.metronome
package api

import com.eclipsesource.schema.SchemaValidator
import com.wix.accord.{Failure, Success, Validator}
import mesosphere.marathon.api.v2.Validation
import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.libs.json._
import play.api.mvc._

class RestController(cc: ControllerComponents) extends AbstractController(cc) {

  import dcos.metronome.api.v1.models.JsErrorWrites

  implicit def jsonWritable[T <: Any](implicit w: Writes[T], codec: Codec, request: RequestHeader): Writeable[T] = {
    implicit val contentType: ContentTypeOf[T] = ContentTypeOf[T](Some(ContentTypes.JSON))
    Writeable(t => codec.encode(Json.stringify(w.writes(t))))
  }

  object validate {

    val schemaValidator = SchemaValidator()

    def json[A](implicit reader: Reads[A], schema: JsonSchema[A], validator: Validator[A]): BodyParser[A] = {
      jsonWith[A](identity)
    }

    def jsonWith[A](
        fn: A => A
    )(implicit reader: Reads[A], schema: JsonSchema[A], validator: Validator[A]): BodyParser[A] = {
      BodyParser("json reader and validator") { request =>
        import play.api.libs.iteratee.Execution.Implicits.trampoline

        def validateObject(a: A): Either[Result, A] =
          validator(a) match {
            case Success => Right(a)
            case f: Failure => Left(UnprocessableEntity(Validation.failureWrites.writes(f)))
          }

        def readObject(jsValue: JsValue): Either[Result, A] = {
          jsValue.validate(reader) match {
            case JsSuccess(value, _) => validateObject(fn(value))
            case error: JsError => Left(UnprocessableEntity(Json.toJson(error)))
          }
        }

        def schemaValidate(jsValue: JsValue): Either[Result, A] = {
          schemaValidator.validate(schema.schemaType, jsValue) match {
            case JsSuccess(value, _) => readObject(value)
            case error: JsError => Left(UnprocessableEntity(Json.toJson(error)))
          }
        }

        parse.json(request).map {
          case Left(simpleResult) => Left(simpleResult)
          case Right(jsValue) => schemaValidate(jsValue)
        }
      }
    }
  }
}

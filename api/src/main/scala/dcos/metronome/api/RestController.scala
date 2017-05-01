package dcos.metronome.api

import com.eclipsesource.schema.SchemaValidator
import com.wix.accord.{ Validator, Success, Failure }
import mesosphere.marathon.api.v2.Validation
import play.api.http.{ ContentTypes, ContentTypeOf, Writeable }
import play.api.libs.json._
import play.api.mvc._

class RestController extends Controller {

  import dcos.metronome.api.v1.models.JsErrorWrites

  implicit def jsonWritable[T <: Any](implicit w: Writes[T], codec: Codec, request: RequestHeader): Writeable[T] = {
    implicit val contentType = ContentTypeOf[T](Some(ContentTypes.JSON))
    Writeable(t => codec.encode(Json.stringify(w.writes(t))))
  }

  object validate extends BodyParsers {

    def jsonWithValidator[A](validator: Validator[A])(implicit reader: Reads[A], schema: JsonSchema[A]): BodyParser[A] = {
      json[A](reader, schema, validator)
    }

    def json[A](implicit reader: Reads[A], schema: JsonSchema[A], validator: Validator[A]): BodyParser[A] = {
      jsonWith[A](identity)
    }

    def jsonWith[A](fn: A => A)(implicit reader: Reads[A], schema: JsonSchema[A], validator: Validator[A]): BodyParser[A] = {
      BodyParser("json reader and validator") { request =>
        import play.api.libs.iteratee.Execution.Implicits.trampoline

        def validateObject(a: A): Either[Result, A] = validator(a) match {
          case Success    => Right(a)
          case f: Failure => Left(UnprocessableEntity(Validation.failureWrites.writes(f)))
        }

        def readObject(jsValue: JsValue): Either[Result, A] = {
          jsValue.validate(reader) match {
            case JsSuccess(value, _) => validateObject(fn(value))
            case error: JsError      => Left(UnprocessableEntity(Json.toJson(error)))
          }
        }

        def schemaValidate(jsValue: JsValue): Either[Result, A] = {
          SchemaValidator.validate(schema.schemaType)(jsValue) match {
            case JsSuccess(value, _) => readObject(value)
            case error: JsError      => Left(UnprocessableEntity(Json.toJson(error)))
          }
        }

        parse.json(request).map {
          case Left(simpleResult) => Left(simpleResult)
          case Right(jsValue)     => schemaValidate(jsValue)
        }
      }
    }
  }
}

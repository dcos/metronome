package dcos.metronome.api

import com.wix.accord.{ Validator, Success, Failure }
import mesosphere.marathon.api.v2.Validation
import play.api.http.{ LazyHttpErrorHandler, ContentTypes, ContentTypeOf, Writeable }
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Future

class RestController extends Controller {

  implicit def jsonWritable[T <: Any](implicit w: Writes[T], codec: Codec, request: RequestHeader): Writeable[T] = {
    implicit val contentType = ContentTypeOf[T](Some(ContentTypes.JSON))
    Writeable(t => codec.encode(Json.stringify(w.writes(t))))
  }

  object validate extends BodyParsers {

    def json[A](implicit reader: Reads[A], validator: Validator[A]): BodyParser[A] = {
      BodyParser("json reader and validator") { request =>
        import play.api.libs.iteratee.Execution.Implicits.trampoline
        parse.json(request) mapFuture {
          case Left(simpleResult) =>
            Future.successful(Left(simpleResult))
          case Right(jsValue) =>
            jsValue.validate(reader) map { a =>
              validator(a) match {
                case Success    => Future.successful(Right(a))
                case f: Failure => Future.successful(Left(UnprocessableEntity(Validation.failureWrites.writes(f))))
              }
            } recoverTotal { jsError =>
              val msg = s"Json validation error ${JsError.toFlatForm(jsError)}"
              LazyHttpErrorHandler.onClientError(request, BAD_REQUEST, msg).map(Left.apply)
            }
        }
      }
    }
  }
}

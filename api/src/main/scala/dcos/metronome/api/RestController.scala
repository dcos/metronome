package dcos.metronome.api

import play.api.http.{ ContentTypes, ContentTypeOf, Writeable }
import play.api.libs.json.{ Json, JsValue, Writes }
import play.api.mvc.{ Controller, RequestHeader, Codec }

class RestController extends Controller {

  implicit def jsonWritable[T <: Any](implicit w: Writes[T], codec: Codec, request: RequestHeader): Writeable[T] = {
    implicit val contentType = ContentTypeOf[T](Some(ContentTypes.JSON))
    Writeable(t => codec.encode(Json.stringify(w.writes(t))))
  }
}

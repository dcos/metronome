package dcos.metronome.api

import net.jcazevedo.moultingyaml.{ YamlValue, _ }
import play.api.http.{ ContentTypeOf, MimeTypes, Writeable }
import play.api.mvc.{ Accepting, BodyParser, Codec, Controller }

import scala.concurrent.Future

trait YamlContent { self: Controller =>

  import scala.concurrent.ExecutionContext.Implicits.global

  val AcceptJson = Accepting(MimeTypes.JSON)
  val AcceptYaml = Accepting("application/x-yaml")

  implicit val YamlContentType = ContentTypeOf[YamlValue](Some("application/x-yaml"))

  implicit def writeableOfYamlValue(implicit codec: Codec): Writeable[YamlValue] = {
    Writeable(yaml => codec.encode(yaml.prettyPrint))
  }

  def parseYaml: BodyParser[YamlValue] = parse.when(
    _.contentType.exists(m => m.equalsIgnoreCase("text/yaml") || m.equalsIgnoreCase("application/x-yaml")),
    parse.tolerantText.map(_.parseYaml),
    _ => Future.successful(BadRequest("Expecting text/yaml or application/x-yaml body"))
  )
}

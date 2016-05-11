package dcos.metronome.api.v1

import dcos.metronome.greeting.Greeting
import net.jcazevedo.moultingyaml._
import play.api.libs.json.Json

package object models {

  implicit val GreetingFormat = Json.format[Greeting]

  implicit object GreetingYamlFormat extends YamlFormat[Greeting] {

    override def write(g: Greeting): YamlValue = {
      YamlObject(
        YamlString("id") -> YamlNumber(g.id),
        YamlString("message") -> YamlString(g.message),
        YamlString("name") -> YamlString(g.name)
      )
    }

    override def read(yaml: YamlValue): Greeting = yaml.asYamlObject
      .getFields(
        YamlString("id"),
        YamlString("message"),
        YamlString("name")) match {
          case Seq(YamlNumber(id: Int), YamlString(message), YamlString(name)) => Greeting(id, message, name)
        }
  }
}

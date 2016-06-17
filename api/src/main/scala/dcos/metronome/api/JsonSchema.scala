package dcos.metronome.api

import com.eclipsesource.schema.SchemaType
import mesosphere.marathon.io.IO
import play.api.libs.json.Json

import scala.reflect.ClassTag

/**
  * Represents a json schema for a given model type
  * @tparam Model the type this schema is defined on
  */
trait JsonSchema[Model] {

  /**
    * The runtime class tag of the model
    */
  def classTag: ClassTag[Model]

  /**
    * The json schema type
    */
  def schemaType: SchemaType

}

object JsonSchema {
  def fromResource[T](fromClassPath: String)(implicit ct: ClassTag[T]): JsonSchema[T] = {
    IO.withResource(fromClassPath) { resource =>
      val schema = Json.parse(resource).as[SchemaType]
      new JsonSchema[T] {
        override def classTag: ClassTag[T] = ct
        override def schemaType: SchemaType = schema
      }
    }.getOrElse(throw new IllegalArgumentException(s"Schema not found: $fromClassPath"))
  }
}

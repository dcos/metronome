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

  /**
    * The path to the schema location
    */
  def schemaLocation: String

}

object JsonSchema {
  def fromResource[T](fromClassPath: String)(implicit ct: ClassTag[T]): JsonSchema[T] = {
    IO.withResource(fromClassPath) { resource =>
      val schema = Json.parse(resource).as[SchemaType]
      new JsonSchema[T] {
        override def classTag: ClassTag[T] = ct
        override def schemaType: SchemaType = schema
        //we serve the public directory: the resource path is the same as the schema location path
        override def schemaLocation: String = fromClassPath
      }
    }.getOrElse(throw new IllegalArgumentException(s"Schema not found: $fromClassPath"))
  }
}

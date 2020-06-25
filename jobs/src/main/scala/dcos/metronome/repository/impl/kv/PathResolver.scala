package dcos.metronome
package repository.impl.kv

/**
  * PathResolver can translate a model specific Id into a path in the store.
  * @tparam Id the type of Id this resolver can work with.
  */
trait PathResolver[Id] {

  /**
    * Return the base path for the given entity
    * @return
    */
  def basePath: String

  /**
    * Translate a given id to a path.
    * @param id the id to translate
    * @return the path of this id.
    */
  def toPath(id: Id): String

  /**
    * Translate the path back into an id.
    * @param path the path to translate.
    * @return the id of this path.
    */
  def fromPath(path: String): Id
}

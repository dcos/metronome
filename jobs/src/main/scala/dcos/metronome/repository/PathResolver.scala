package dcos.metronome.repository

/**
  * PathResolver can translate a model specific Id into a path in the store.
  * @tparam Id the type of Id this resolver can work with.
  */
trait PathResolver[Id] {

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

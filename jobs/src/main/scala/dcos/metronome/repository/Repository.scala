package dcos.metronome.repository

import dcos.metronome.PersistenceFailed
import mesosphere.marathon.StoreCommandFailedException

import scala.concurrent.Future

/**
  * A repository can store model of type Model which have an id of type Id
  *
  * @tparam Id the Id type of the model
  * @tparam Model the type to handle of that Model.
  */
trait Repository[Id, Model] {

  /**
    * Return all available ids.
    *
    * @return all available persistedids.
    * @throws StoreCommandFailedException
    *         in the case of an underlying store problems.
    */
  def ids(): Future[Iterable[Id]]

  /**
    * Get the model with the given id.
    *
    * @param id the id of the model to load.
    * @return Some(model) if the model is existent, otherwise None.
    * @throws StoreCommandFailedException
    *         in the case of an underlying store problems.
    */
  def get(id: Id): Future[Option[Model]]

  /**
    * Create the model with the given id.
    *
    * @param id the id of the model to create.
    * @param model the model to persist.
    * @return the stored model.
    * @throws StoreCommandFailedException
    *         in the case of an already existing entity or underlying store problems.
    */
  def create(id: Id, model: Model): Future[Model]

  /**
    * Update the given model.
    *
    * @param id the id of the model.
    * @param change the change function, that needs to be applied on the existing model.
    * @return the update model
    * @throws StoreCommandFailedException
    *         in the case of concurrent modifications or underlying store problems.
    * @throws PersistenceFailed
    *         if there is no model with this id, or the model could not be read.
    */
  def update(id: Id, change: Model => Model): Future[Model]

  /**
    * Delete the model with the given id.
    *
    * @param id the id of the model to delete.
    * @return true if the entity was deleted and false if not existent.
    *         In case of a storage specific failure, a StoreCommandFailedException is thrown.
    * @throws StoreCommandFailedException
    *         if the item could not get deleted or underlying store problems.
    */
  def delete(id: Id): Future[Boolean]
}


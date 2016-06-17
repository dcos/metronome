package dcos.metronome.repository.impl

import dcos.metronome.PersistenceFailed
import dcos.metronome.repository.Repository
import mesosphere.marathon.StoreCommandFailedException

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * For testing purposes only.
  */
class InMemoryRepository[Id, Model] extends Repository[Id, Model] {

  val models: TrieMap[Id, Model] = TrieMap.empty[Id, Model]

  override def ids(): Future[Iterable[Id]] = Future.successful(models.keys)

  override def update(id: Id, change: (Model) => Model): Future[Model] = {
    models.get(id) match {
      case Some(model) =>
        try {
          val changed = change(model)
          models.update(id, changed)
          Future.successful(changed)
        } catch {
          case NonFatal(ex) => Future.failed(ex)
        }
      case None =>
        Future.failed(new PersistenceFailed(id.toString, "No model with this id"))
    }
  }

  override def get(id: Id): Future[Option[Model]] = Future.successful(models.get(id))

  override def delete(id: Id): Future[Boolean] = Future.successful(models.remove(id).isDefined)

  override def create(id: Id, create: Model): Future[Model] = {
    models.get(id) match {
      case Some(model) =>
        Future.failed(new StoreCommandFailedException("Model with id already exists."))
      case None =>
        models += id -> create
        Future.successful(create)
    }
  }
}

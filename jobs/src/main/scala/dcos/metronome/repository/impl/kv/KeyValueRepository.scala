package dcos.metronome
package repository.impl.kv

import dcos.metronome.PersistenceFailed
import dcos.metronome.repository.Repository
import mesosphere.util.state.{ PersistentEntity, PersistentStore, PersistentStoreWithNestedPathsSupport }

import scala.concurrent.{ ExecutionContext, Future }

abstract class KeyValueRepository[Id, Model](
    pathResolver:    PathResolver[Id],
    marshaller:      EntityMarshaller[Model],
    store:           PersistentStoreWithNestedPathsSupport,
    implicit val ec: ExecutionContext
) extends Repository[Id, Model] {

  override def ids(): Future[Iterable[Id]] = {
    store.allIds(pathResolver.basePath).map(paths => paths.map(pathResolver.fromPath))
  }

  override def update(id: Id, change: Model => Model): Future[Model] = {

    val path = pathResolver.toPath(id)

    def updateEntity(entity: PersistentEntity): Future[Model] = {
      val changed = marshaller.fromBytes(entity.bytes) match {
        case Some(model) => change(model)
        case None        => throw PersistenceFailed(id.toString, "Entity could not be deserialized!")
      }
      store.update(entity.withNewContent(marshaller.toBytes(changed))).map(_ => changed)
    }

    store.load(path).flatMap {
      case Some(entity) => updateEntity(entity)
      case None         => Future.failed(PersistenceFailed(id.toString, "No entity with this Id!"))
    }
  }

  override def get(id: Id): Future[Option[Model]] = {
    val path = pathResolver.toPath(id)
    store.load(path).map {
      _.flatMap(entity => marshaller.fromBytes(entity.bytes))
    }
  }

  override def delete(id: Id): Future[Boolean] = {
    store.delete(pathResolver.toPath(id))
  }

  override def create(id: Id, model: Model): Future[Model] = {
    val path = pathResolver.toPath(id)
    val marshalled = marshaller.toBytes(model)
    store.create(path, marshalled).map(_ => model)
  }
}

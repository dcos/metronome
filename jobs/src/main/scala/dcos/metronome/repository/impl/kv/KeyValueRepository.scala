package dcos.metronome.repository.impl.kv

import dcos.metronome.PersistenceFailed
import dcos.metronome.repository.Repository
import mesosphere.util.state.{ PersistentEntity, PersistentStore }

import scala.concurrent.{ ExecutionContext, Future }

abstract class KeyValueRepository[Id, Model](
    pathResolver:    PathResolver[Id],
    serializer:      EntityMarshaller[Model],
    store:           PersistentStore,
    implicit val ec: ExecutionContext
) extends Repository[Id, Model] {

  override def all(): Future[Iterable[Id]] = {
    store.allIds().map(paths => paths.map(pathResolver.fromPath))
  }

  override def update(id: Id, change: Model => Model): Future[Model] = {

    val path = pathResolver.toPath(id)

    def updateEntity(entity: PersistentEntity): Future[Model] = {
      val changed = serializer.fromBytes(entity.bytes) match {
        case Some(model) => change(model)
        case None        => throw PersistenceFailed(id.toString, "Entity could not be deserialized!")
      }
      store.update(entity.withNewContent(serializer.toBytes(changed))).map(_ => changed)
    }

    store.load(path).flatMap {
      case Some(entity) => updateEntity(entity)
      case None         => Future.failed(PersistenceFailed(id.toString, "No entity with this Id!"))
    }
  }

  override def get(id: Id): Future[Option[Model]] = {
    val path = pathResolver.toPath(id)
    store.load(path).map {
      _.flatMap(entity => serializer.fromBytes(entity.bytes))
    }
  }

  override def delete(id: Id): Future[Boolean] = {
    store.delete(pathResolver.toPath(id))
  }

  override def create(id: Id, model: Model): Future[Model] = {
    val path = pathResolver.toPath(id)
    val serialized = serializer.toBytes(model)
    store.create(path, serialized).map(_ => model)
  }
}

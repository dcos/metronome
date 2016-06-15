package dcos.metronome.repository.impl.kv

import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.state.PathId
import mesosphere.util.state.{ PersistentEntity, PersistentStoreWithNestedPathsSupport }
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.concurrent.ScalaFutures._
import org.slf4j.Logger

import scala.concurrent.Future

class KeyValueRepositoryTest extends FunSuite with Matchers with Mockito {
  test("ids") {
    val f = new Fixture
    f.store.allIds(f.basePath).returns(Future.successful(Seq("foo.bar")))

    f.repository.ids().futureValue should be(Seq(f.id))
  }

  test("update") {
    val f = new Fixture
    val updatedModel = Model(PathId("/new/id"))

    f.store.load(f.pathResolver.toPath(f.model.id)).returns(Future.successful(Some(f.storeEntity)))
    f.repository.update(f.model.id, _ => updatedModel)

    verify(f.store).update(StoreEntity(updatedModel))
  }

  test("get") {
    val f = new Fixture
    f.store.load(f.pathResolver.toPath(f.model.id)).returns(Future.successful(Some(f.storeEntity)))

    f.repository.get(f.model.id).futureValue should be(Some(f.model))
  }

  test("delete") {
    val f = new Fixture
    f.store.delete(f.pathResolver.toPath(f.id)).returns(Future.successful(true))

    f.repository.delete(f.id).futureValue should be (true)

    verify(f.store).delete(f.modelPath)
    noMoreInteractions(f.store)
  }

  test("create") {
    val f = new Fixture

    f.store.create(any, any).returns(Future.successful(f.storeEntity))

    f.repository.create(f.id, f.model).futureValue should be (f.model)

    verify(f.store).create(f.modelPath, ModelMarshaller.toBytes(f.model))
    noMoreInteractions(f.store)
  }

  case class StoreEntity(model: Model) extends PersistentEntity {
    override def id: String = model.id.toString
    override def withNewContent(updated: IndexedSeq[Byte]): PersistentEntity =
      StoreEntity(ModelMarshaller.fromBytes(updated).get)
    override def bytes: IndexedSeq[Byte] = ModelMarshaller.toBytes(model)
  }

  case class Model(id: PathId)
  object ModelMarshaller extends EntityMarshaller[Model] {
    override def log: Logger = ???
    override def toBytes(model: Model): IndexedSeq[Byte] = model.id.toString.getBytes
    override def fromBytes(bytes: IndexedSeq[Byte]): Option[Model] =
      Some(Model(PathId(new String(bytes.map(_.toChar).toArray))))
  }

  class Fixture {
    val ec = scala.concurrent.ExecutionContext.global

    val basePath = "base"
    val pathResolver = PathIdPathResolver(basePath)
    val marshaller = ModelMarshaller
    val store: PersistentStoreWithNestedPathsSupport = mock[PersistentStoreWithNestedPathsSupport]
    val repository = new KeyValueRepository[PathId, Model](pathResolver, marshaller, store, ec) {}

    val id = PathId("/foo/bar")
    val model = Model(id)
    val modelPath = pathResolver.toPath(id)
    val storeEntity = StoreEntity(model)
  }
}

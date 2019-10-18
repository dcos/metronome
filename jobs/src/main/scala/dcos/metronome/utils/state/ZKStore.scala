package dcos.metronome
package utils.state

import java.nio.ByteBuffer
import java.util.UUID

import akka.util.CompactByteString
import com.fasterxml.uuid.impl.UUIDUtil
import com.google.protobuf.{ ByteString, InvalidProtocolBufferException }
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.core.storage.store.impl.zk.{ ExistsResult, RichCuratorFramework }
import mesosphere.marathon.{ Protos, StoreCommandFailedException }
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.server.ByteBufferInputStream

import scala.concurrent.{ ExecutionContext, Future }

case class CompressionConf(enabled: Boolean, sizeLimit: Long)

class ZKStore(val richCurator: RichCuratorFramework, rootPath: String, compressionConf: CompressionConf) extends PersistentStore
    with PersistentStoreManagement with PersistentStoreWithNestedPathsSupport with StrictLogging {

  private[this] implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private[this] def fullPath(subPath: ID): ID = {
    rootPath + "/" + subPath
  }

  /**
    * Fetch data and return entity.
    * The entity is returned also if it is not found in zk, since it is needed for the store operation.
    */
  override def load(key: ID): Future[Option[ZKEntity]] = {
    val path = fullPath(key)
    richCurator
      .data(path)
      .map{ node =>
        val zkData = ZKData(node.data.asByteBuffer)
        Some(ZKEntity(node.path, zkData, Some(node.stat.getVersion)))
      }
      .recover { case _: NoNodeException => None }
      .recover(exceptionTransform(s"Could not load key $key"))
  }

  override def create(key: ID, content: IndexedSeq[Byte]): Future[ZKEntity] = {
    val path = fullPath(key)
    val data = ZKData(path, UUID.randomUUID(), content)
    val dataArray = CompactByteString(data.toProto(compressionConf).toByteArray)
    richCurator
      .create(path, Some(dataArray))
      .map{ path =>ZKEntity(path, data, Some(0)) }
      .recover(exceptionTransform(s"Can not create entity $path"))
  }

  /**
    * This will store a previously fetched entity.
    * The entity will be either created or updated, depending on the read state.
    *
    * @return Some value, if the store operation is successful otherwise None
    */
  override def update(entity: PersistentEntity): Future[ZKEntity] = {
    val zk = zkEntity(entity)
    val version = zk.version.getOrElse (
      throw new StoreCommandFailedException(s"Can not store entity $entity, since there is no version!"))

    val dataArray = CompactByteString(zk.data.toProto(compressionConf).toByteArray)

    richCurator
      .setData(zk.id, dataArray, false, Some(version))
      .map{ node => zk.copy(version = Some(node.stat.getVersion)) }
      .recover(exceptionTransform(s"Can not update entity $entity"))
  }

  /**
    * Delete an entry with given identifier.
    */
  override def delete(key: ID): Future[Boolean] = {
    val path = fullPath(key)
    richCurator
      .delete(path)
      .map(_ => true)
      .recover { case _: NoNodeException => false }
      .recover(exceptionTransform(s"Can not delete entity $path"))
  }

  override def allIds(): Future[Seq[ID]] = {
    richCurator
      .children(rootPath)
      .map(_.children)
      .recover(exceptionTransform("Can not list all identifiers"))
  }

  override def allIds(parent: ID): Future[Seq[ID]] = {
    val path = fullPath(parent)
    richCurator
      .children(path)
      .map(_.children)
      .recover(exceptionTransform(s"Can not list children of $path"))
  }

  private[this] def exceptionTransform[T](errorMessage: => String): PartialFunction[Throwable, T] = {
    case ex: KeeperException => throw new StoreCommandFailedException(errorMessage, ex)
  }

  private[this] def zkEntity(entity: PersistentEntity): ZKEntity = {
    entity match {
      case zk: ZKEntity => zk
      case _            => throw new IllegalArgumentException(s"Can not handle this kind of entity: ${entity.getClass}")
    }
  }

  private[this] def createAbsolutePath(path: String): Future[Unit] = {
    logger.info(s"Check if ${path} exists...")
    richCurator
      .exists(path)
      .recover { case _: NoNodeException => ExistsResult(path, null) }
      .map{ res =>
        if (res.stat == null) {
          logger.info(s"Path ${path} does not exist, create now")
          richCurator.create(path, None, creatingParentsIfNeeded = true, creatingParentContainersIfNeeded = true).map(_ => ())
        } else {
          logger.info(s"Path ${path} already exists, skip creation")
        }
      }
  }

  override def initialize(): Future[Unit] = {
    logger.info(s"Initialize ZKStore with rootPath '${rootPath}")
    createAbsolutePath(rootPath).map(_ => ())
  }

  override def createPath(path: String): Future[Unit] = {
    createAbsolutePath(fullPath(path))
  }
}

case class ZKEntity(id: String, data: ZKData, version: Option[Int] = None) extends PersistentEntity {

  override def withNewContent(updated: IndexedSeq[Byte]): PersistentEntity = copy(data = data.copy(bytes = updated))
  override def bytes: IndexedSeq[Byte] = data.bytes
}

case class ZKData(name: String, uuid: UUID, bytes: IndexedSeq[Byte] = Vector.empty) {
  def toProto(compression: CompressionConf): Protos.ZKStoreEntry = {
    val (data, compressed) =
      if (compression.enabled && bytes.length > compression.sizeLimit) (IO.gzipCompress(bytes.toArray), true)
      else (bytes.toArray, false)
    Protos.ZKStoreEntry.newBuilder()
      .setName(name)
      .setUuid(ByteString.copyFromUtf8(uuid.toString))
      .setCompressed(compressed)
      .setValue(ByteString.copyFrom(data))
      .build()
  }
}
object ZKData {
  import IO.{ gzipUncompress => uncompress }

  def apply(bytes: ByteBuffer): ZKData = {
    try {
      val byteStream = new ByteBufferInputStream(bytes)
      val proto = Protos.ZKStoreEntry.parseFrom(byteStream)
      val content = if (proto.getCompressed) uncompress(proto.getValue.toByteArray) else proto.getValue.toByteArray
      new ZKData(proto.getName, UUIDUtil.uuid(proto.getUuid.toByteArray), content.to[IndexedSeq])
    } catch {
      case ex: InvalidProtocolBufferException =>
        throw new StoreCommandFailedException(s"Can not deserialize Protobuf from ByteBuffer", ex)
    }
  }

  def apply(bytes: Array[Byte]): ZKData = {
    try {
      val proto = Protos.ZKStoreEntry.parseFrom(bytes)
      val content = if (proto.getCompressed) uncompress(proto.getValue.toByteArray) else proto.getValue.toByteArray
      new ZKData(proto.getName, UUIDUtil.uuid(proto.getUuid.toByteArray), content.to[IndexedSeq])
    } catch {
      case ex: InvalidProtocolBufferException =>
        throw new StoreCommandFailedException(s"Can not deserialize Protobuf from ${bytes.length}", ex)
    }
  }
}

object ZKStore {

}

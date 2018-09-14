package dcos.metronome
package utils.state

import com.google.protobuf.InvalidProtocolBufferException
import mesosphere.marathon.StoreCommandFailedException
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class MarathonStore[S <: MarathonState[_, S]](
  store:    PersistentStore,
  newState: () => S,
  prefix:   String)(implicit ct: ClassTag[S]) extends EntityStore[S] {

  import scala.concurrent.ExecutionContext.Implicits.global
  private[this] val log = LoggerFactory.getLogger(getClass)

  private[this] lazy val lockManager = LockManager.create()

  def fetch(key: String): Future[Option[S]] = {
    log.debug(s"Fetch $prefix$key")
    store.load(prefix + key)
      .map {
        _.map { entity =>
          stateFromBytes(entity.bytes.toArray)
        }
      }
      .recover {
        case _: InvalidProtocolBufferException =>
          log.warn(s"Unable to read $key due to a protocol buffer exception")
          None
        case NonFatal(ex) =>
          throw new StoreCommandFailedException(s"Could not fetch ${ct.runtimeClass.getSimpleName} with key: $key", ex)
      }
  }

  def modify(key: String, onSuccess: S => Unit = _ => ())(f: Update): Future[S] = {
    lockManager.executeSequentially(key) {
      log.debug(s"Modify $prefix$key")
      val res = store.load(prefix + key).flatMap {
        case Some(entity) =>
          val updated = f(() => stateFromBytes(entity.bytes.toArray))
          val updatedEntity = entity.withNewContent(updated.toProtoByteArray.to[IndexedSeq])
          store.update(updatedEntity)
        case None =>
          val created = f(() => newState()).toProtoByteArray
          store.create(prefix + key, created.to[IndexedSeq])
      }
      res.map { entity =>
        val result = stateFromBytes(entity.bytes.toArray)
        onSuccess(result)
        result
      }.recover(exceptionTransform(s"Could not modify ${ct.runtimeClass.getSimpleName} with key: $key"))
    }
  }

  def expunge(key: String, onSuccess: () => Unit = () => ()): Future[Boolean] = lockManager.executeSequentially(key) {
    log.debug(s"Expunge $prefix$key")
    store.delete(prefix + key).map { result =>
      onSuccess()
      result
    }.recover(exceptionTransform(s"Could not expunge ${ct.runtimeClass.getSimpleName} with key: $key"))
  }

  def names(): Future[Seq[String]] = {
    store.allIds()
      .map {
        _.collect {
          case name: String if name startsWith prefix => name.replaceFirst(prefix, "")
        }(collection.breakOut)
      }
      .recover(exceptionTransform(s"Could not list names for ${ct.runtimeClass.getSimpleName}"))
  }

  private[this] def exceptionTransform[T](errorMessage: String): PartialFunction[Throwable, T] = {
    case NonFatal(ex) => throw new StoreCommandFailedException(errorMessage, ex)
  }

  private def stateFromBytes(bytes: Array[Byte]): S = {
    newState().mergeFromProto(bytes)
  }

  override def toString: String = s"MarathonStore($prefix)"
}

package dcos.metronome.repository.impl.kv

import org.slf4j.Logger

import scala.util.control.NonFatal

/**
  * Can serialize a model to bytes and back again.
  *
  * @tparam Model the model type the marshaller can handle.
  */
abstract class EntityMarshaller[Model] {
  def log: Logger

  /**
    * Marshal the model to bytes.
    *
    * @param model the model to marshal
    * @return the marshalled bytes of the given model
    */
  def toBytes(model: Model): IndexedSeq[Byte]

  /**
    * Unmarshal bytes into a model
    *
    * @param bytes the bytes to unmarshal.
    * @return Some(model) if the model can be read from the given bytes, otherwise none.
    */
  def fromBytes(bytes: IndexedSeq[Byte]): Option[Model]

  protected def safeConversion(fn: => Model): Option[Model] = {
    try {
      Some(fn)
    } catch {
      case NonFatal(e) =>
        log.error("Error unmarshalling entity", e)
        None
    }
  }
}

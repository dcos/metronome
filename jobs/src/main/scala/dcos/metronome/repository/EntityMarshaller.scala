package dcos.metronome.repository

/**
  * Can serialize a model to bytes and back again.
  * @tparam Model the model type the marshaller can handle.
  */
trait EntityMarshaller[Model] {

  /**
    * Marshal the model to bytes.
    * @param model the model to marshal
    * @return the marshalled bytes of the given model
    */
  def toBytes(model: Model): IndexedSeq[Byte]

  /**
    * Unmarsjal bytes into a model
    * @param bytes the bytes to unmarshal.
    * @return Some(model) if the model can be read from the given bytes, otherwise none.
    */
  def fromBytes(bytes: IndexedSeq[Byte]): Option[Model]
}

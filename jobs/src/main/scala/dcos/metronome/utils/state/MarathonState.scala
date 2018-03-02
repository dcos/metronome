package dcos.metronome.utils.state

import com.google.protobuf.Message
import mesosphere.marathon.state.Timestamp

trait MarathonState[M <: Message, T <: MarathonState[M, _]] {

  def mergeFromProto(message: M): T

  def mergeFromProto(bytes: Array[Byte]): T

  def toProto: M

  def toProtoByteArray: Array[Byte] = toProto.toByteArray

  def version: Timestamp
}

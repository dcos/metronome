package dcos.metronome
package model

sealed trait Volume

case class HostVolume(containerPath: String, hostPath: String, mode: Mode) extends Volume

case class SecretVolume(containerPath: String, secret: String) extends Volume

sealed trait Mode

object Mode {
  case object RO extends Mode
  case object RW extends Mode

  val names: Map[String, Mode] = Map("RO" -> RO, "RW" -> RW)
  val modeNames: Map[Mode, String] = names.map { case (a, b) => (b, a) }

  def name(mode: Mode): String = modeNames(mode)
  def unapply(name: String): Option[Mode] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}

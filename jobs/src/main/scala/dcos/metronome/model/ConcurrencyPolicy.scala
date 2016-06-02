package dcos.metronome.model

sealed trait ConcurrencyPolicy
object ConcurrencyPolicy {

  val names: Set[String] = Set("allow", "forbid", "replace")

  def unapply(name: String): Option[ConcurrencyPolicy] = name match {
    case "allow"   => Some(Allow)
    case "forbid"  => Some(Forbid)
    case "replace" => Some(Replace)
    case _         => None
  }

  def name(policy: ConcurrencyPolicy): String = policy match {
    case Allow   => "allow"
    case Forbid  => "forbid"
    case Replace => "replace"
  }

  case object Allow extends ConcurrencyPolicy
  case object Forbid extends ConcurrencyPolicy
  case object Replace extends ConcurrencyPolicy
}


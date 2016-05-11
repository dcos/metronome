package dcos.metronome.model

sealed trait ConcurrencyPolicy
object ConcurrencyPolicy {
  def unapply(name: String): Option[ConcurrencyPolicy] = name match {
    case "allow"  => Some(AllowConcurrentRuns)
    case "forbid" => Some(ForbidConcurrentRuns)
    case _        => None
  }
  def name(policy: ConcurrencyPolicy): String = policy match {
    case AllowConcurrentRuns  => "allow"
    case ForbidConcurrentRuns => "forbid"
  }
}
case object AllowConcurrentRuns extends ConcurrencyPolicy
case object ForbidConcurrentRuns extends ConcurrencyPolicy


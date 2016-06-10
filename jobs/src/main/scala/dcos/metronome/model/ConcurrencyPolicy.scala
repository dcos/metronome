package dcos.metronome.model

sealed trait ConcurrencyPolicy
object ConcurrencyPolicy {
  case object Allow extends ConcurrencyPolicy
  case object Forbid extends ConcurrencyPolicy
  case object Replace extends ConcurrencyPolicy

  val names: Map[String, ConcurrencyPolicy] = Map(
    "ALLOW" -> Allow,
    "FORBID" -> Forbid,
    "REPLACE" -> Replace
  )
  val concurrencyPolicyNames: Map[ConcurrencyPolicy, String] = names.map{ case (a, b) => (b, a) }

  def name(concurrencyPolicy: ConcurrencyPolicy): String = concurrencyPolicyNames(concurrencyPolicy)
  def unapply(name: String): Option[ConcurrencyPolicy] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}


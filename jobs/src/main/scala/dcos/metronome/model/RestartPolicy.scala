package dcos.metronome
package model

sealed trait RestartPolicy
object RestartPolicy {
  case object Never extends RestartPolicy
  case object OnFailure extends RestartPolicy

  val names: Map[String, RestartPolicy] = Map("NEVER" -> Never, "ON_FAILURE" -> OnFailure)
  val restartPolicyNames: Map[RestartPolicy, String] = names.map { case (a, b) => (b, a) }

  def name(restartPolicy: RestartPolicy): String = restartPolicyNames(restartPolicy)
  def unapply(name: String): Option[RestartPolicy] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}

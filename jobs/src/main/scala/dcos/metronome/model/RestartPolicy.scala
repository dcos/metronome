package dcos.metronome.model

sealed trait RestartPolicy

object RestartPolicy {

  def unapply(name: String): Option[RestartPolicy] = name match {
    case "never"     => Some(RestartNever)
    case "onFailure" => Some(RestartOnFailure)
    case _           => None
  }
  def name(policy: RestartPolicy): String = policy match {
    case RestartNever     => "never"
    case RestartOnFailure => "onFailure"
  }
}

case object RestartNever extends RestartPolicy
case object RestartOnFailure extends RestartPolicy

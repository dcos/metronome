package dcos.metronome.model

case class PlacementSpec(
  constraints: Seq[ConstraintSpec] = PlacementSpec.DefaultConstraints)
object PlacementSpec {
  val DefaultConstraints = Seq.empty[ConstraintSpec]
}

case class ConstraintSpec(attr: String, op: String, value: Option[String])

object ConstraintSpec {

  val AvailableOperations = Set("EQ", "NEQ", "LIKE", "UNLIKE")
  def isValidOperation(name: String): Boolean = AvailableOperations(name)
}


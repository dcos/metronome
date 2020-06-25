package dcos.metronome
package model

case class PlacementSpec(constraints: Seq[ConstraintSpec] = PlacementSpec.DefaultConstraints)
object PlacementSpec {
  val DefaultConstraints = Seq.empty[ConstraintSpec]
}

case class ConstraintSpec(attribute: String, operator: Operator, value: Option[String])

sealed trait Operator { val name: String }
object Operator {
  case object Is extends Operator { val name = "IS" }
  case object Like extends Operator { val name = "LIKE" }
  case object Unlike extends Operator { val name = "UNLIKE" }

  val all = Seq(Is, Like, Unlike)

  /**
    * For backwards compatibility, we map EQ to Is
    */
  val names: Map[String, Operator] =
    Map(all.map { op => op.name -> op }: _*) + ("EQ" -> Is)
  def unapply(name: String): Option[Operator] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}

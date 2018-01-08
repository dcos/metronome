package dcos.metronome
package model

import scala.collection.immutable.Seq

case class PlacementSpec(
  constraints: Seq[ConstraintSpec] = PlacementSpec.DefaultConstraints)
object PlacementSpec {
  val DefaultConstraints = Seq.empty[ConstraintSpec]
}

case class ConstraintSpec(attribute: String, operator: Operator, value: Option[String])

sealed trait Operator
object Operator {
  case object Eq extends Operator
  case object Like extends Operator
  case object Unlike extends Operator

  val names: Map[String, Operator] = Map(
    "EQ" -> Eq,
    "LIKE" -> Like,
    "UNLIKE" -> Unlike)
  val operatorNames: Map[Operator, String] = names.map{ case (a, b) => (b, a) }

  def name(operator: Operator): String = operatorNames(operator)
  def unapply(name: String): Option[Operator] = names.get(name)
  def isDefined(name: String): Boolean = names.contains(name)
}

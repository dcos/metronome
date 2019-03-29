package dcos.metronome.model

import org.scalatest.{ FunSuite, Matchers }

class PlacementSpecTest extends FunSuite with Matchers {
  test("Operator unapply converts EQ to IS") {
    Operator.unapply("EQ").get.shouldEqual(Operator.Is)
  }
  test("Operator unapply IS") {
    Operator.unapply("IS").get.shouldEqual(Operator.Is)
  }
}

package dcos.metronome.model

import org.scalatest.{ FunSuite, Matchers }

class PlacementSpecSpec extends FunSuite with Matchers {
  test("Operator unapply converts EQ to IS") {
    Operator.unapply("EQ").get.shouldEqual(Operator.Is)
  }
}

package dcos.metronome.model

import dcos.metronome.utils.test.Mockito
import org.scalatest.{ FunSuite, Matchers }

class PlacementSpecSpec extends FunSuite with Matchers {
  test("Operator unapply converts EQ to IS") {
    Operator.unapply("EQ").shouldEqual(Operator.Is)
  }
}

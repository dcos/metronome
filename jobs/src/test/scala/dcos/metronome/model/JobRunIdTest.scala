package dcos.metronome.model

import dcos.metronome.utils.test.Mockito
import mesosphere.marathon.state.PathId
import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }

class JobRunIdTest extends FunSuite with Matchers with Mockito with GivenWhenThen {
  test("Convert simple appId into JobRunId") {
    Given("a simple appId")

    // Marathon core doesn't recognize dot delimiters delimiters we'll get an appId with a single element
    val appId: PathId = PathId("test.20160614133813ap8ZQ")

    When("The id is converted into a JobRunId")
    val jobRunId = JobRunId(appId)

    Then("It is broken apart correctly")
    jobRunId.jobSpecId shouldEqual PathId("test")
    jobRunId.value shouldEqual "20160614133813ap8ZQ"
  }

  test("Convert appId with dots into JobRunId") {
    Given("an appId with multiple dots")
    val appId: PathId = PathId("test.foo.20160614133813ap8ZQ")

    When("The id is converted into a JobRunId")
    val jobRunId = JobRunId(appId)

    Then("It is broken apart correctly")
    jobRunId.jobSpecId shouldEqual PathId("test/foo")
    jobRunId.value shouldEqual "20160614133813ap8ZQ"
  }
}

package dcos.metronome
package model

import dcos.metronome.utils.test.Mockito
import org.scalatest.{FunSuite, GivenWhenThen, Matchers}

class JobRunIdTest extends FunSuite with Matchers with Mockito with GivenWhenThen {
  test("Convert simple appId into JobRunId") {
    Given("a simple appId")

    val jobId: JobId = JobId("test")

    When("The id is converted into a JobRunId")
    val jobRunId = JobRunId(jobId, "20160614133813ap8ZQ")

    Then("It is broken apart correctly")
    jobRunId.jobId shouldEqual JobId("test")
    jobRunId.value shouldEqual "20160614133813ap8ZQ"
  }

  test("Convert appId with dots into JobRunId") {
    Given("an appId with multiple dots")
    val appId: JobId = JobId("test.foo")

    When("The id is converted into a JobRunId")
    val jobRunId = JobRunId(appId, "20160614133813ap8ZQ")

    Then("It is broken apart correctly")
    jobRunId.jobId shouldEqual JobId("test.foo")
    jobRunId.value shouldEqual "20160614133813ap8ZQ"
  }
}

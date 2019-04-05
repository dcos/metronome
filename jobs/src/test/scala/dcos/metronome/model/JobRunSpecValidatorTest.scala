package dcos.metronome
package model

import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }

class JobRunSpecValidatorTest extends FunSuite with Matchers with GivenWhenThen {
  test("Undefined file based secret is invalid") {
    Given("a JobRunSpec with undefined, but referenced secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      volumes = Seq(SecretVolume("path", "undefined-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
  }

  test("Undefined env var secret is invalid") {
    Given("a JobRunSpec with undefined, but referenced secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      env = Map("SECRET_VAR" -> EnvVarSecret("undefined-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
  }

  test("Unused secrets is valid") {
    Given("a JobRunSpec with defined, but unused secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      secrets = Map("secret1" -> SecretDef("unused-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isSuccess shouldBe true
  }
}

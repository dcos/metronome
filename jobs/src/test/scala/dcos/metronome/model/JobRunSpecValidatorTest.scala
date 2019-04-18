package dcos.metronome
package model

import org.scalatest.{ FunSuite, GivenWhenThen, Matchers }

class JobRunSpecValidatorTest extends FunSuite with Matchers with GivenWhenThen {
  test("Undefined file based secret is invalid") {
    Given("a JobRunSpec with undefined, but referenced secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      volumes = Seq(SecretVolume("path", "undefined-vol-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
    result.toFailure.toString.contains("undefined-vol-secret") shouldBe true
  }

  test("Undefined env var secret is invalid") {
    Given("a JobRunSpec with undefined, but referenced secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      env = Map("SECRET_VAR" -> EnvVarSecret("undefined-env-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
    result.toFailure.toString.contains("undefined-env-secret") shouldBe true
  }

  test("Unused secrets is invalid") {
    Given("a JobRunSpec with defined, but unused secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      secrets = Map("secret" -> SecretDef("unused-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
    result.toFailure.toString.contains("unused-secret") shouldBe true
  }

  test("Used secrets are valid") {
    Given("a JobRunSpec with defined and used secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      secrets = Map("secret1" -> SecretDef("secret-def-1"), "secret2" -> SecretDef("secret-def-2")),
      volumes = Seq(SecretVolume("path", "secret1")),
      env = Map("SECRET_VAR" -> EnvVarSecret("secret2")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)
    println(result)

    Then("validation passes")
    result.isSuccess shouldBe true
  }
}

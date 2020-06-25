package dcos.metronome
package model

import com.mesosphere.utils.UnitTest
import com.wix.accord.validate

class JobRunSpecValidatorTest extends UnitTest with ValidationTestLike {
  import Builders._
  "Undefined file based secret is invalid" in {
    Given("a JobRunSpec with undefined, but referenced secrets")
    val jobRunSpec = JobRunSpec(cmd = Some("sleep 1000"), volumes = Seq(SecretVolume("path", "undefined-vol-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result should haveViolations("/" -> JobRunSpecMessages.secretsValidation(Set("undefined-vol-secret"), Set.empty))
  }

  "Undefined env var secret is invalid" in {
    Given("a JobRunSpec with undefined, but referenced secrets")
    val jobRunSpec =
      JobRunSpec(cmd = Some("sleep 1000"), env = Map("SECRET_VAR" -> EnvVarSecret("undefined-env-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
    result.toFailure.toString.contains("undefined-env-secret") shouldBe true
  }

  "Unused secrets is invalid" in {
    Given("a JobRunSpec with defined, but unused secrets")
    val jobRunSpec = JobRunSpec(cmd = Some("sleep 1000"), secrets = Map("secret" -> SecretDef("unused-secret")))

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)

    Then("a validation error is returned")
    result.isFailure shouldBe true
    result.toFailure.toString.contains("unused-secret") shouldBe true
  }

  "Used secrets are valid" in {
    Given("a JobRunSpec with defined and used secrets")
    val jobRunSpec = JobRunSpec(
      cmd = Some("sleep 1000"),
      secrets = Map("secret1" -> SecretDef("secret-def-1"), "secret2" -> SecretDef("secret-def-2")),
      volumes = Seq(SecretVolume("path", "secret1")),
      env = Map("SECRET_VAR" -> EnvVarSecret("secret2"))
    )

    When("The spec is validated")
    val result = JobRunSpec.validJobRunSpec(jobRunSpec)
    println(result)

    Then("validation passes")
    result should be(aSuccess)
  }

  "UCR, command or Docker jobs are valid" in {
    val commandJob = JobRunSpec(cmd = Some("sleep 1000"))
    validate(commandJob) should be(aSuccess)

    val dockerJob = JobRunSpec(docker = Some(DockerSpec(image = "image")))
    validate(dockerJob) should be(aSuccess)

    val ucrJob = JobRunSpec(ucr = Some(newUcrSpec()))
    validate(ucrJob) should be(aSuccess)

  }

  "networking validation" should {
    "prevent multiple network modes from being used" in {
      val jobRunSpec = newJobRunSpec.ucr(networks = Seq(newNetwork.host(), newNetwork.container()))
      validate(jobRunSpec) should haveViolations(
        "/networks" -> JobRunSpecMessages.mixedNetworkModesNotAllowed,
        "/networks" -> JobRunSpecMessages.onlyOneHostOrBridgeNetworkDefinitionAllowed
      )
    }

    "apply individual networking validation for each entry" in {
      val jobRunSpec = newJobRunSpec.ucr(networks = Seq(newNetwork(mode = Network.NetworkMode.Container)))
      validate(jobRunSpec) should haveViolations(
        "/networks(0)/name" -> s"must not be empty ${NetworkValidationMessages.ContainerNetworkHint}"
      )
    }

    "with command tasks" should {
      "not allow container/bridge or container networks" in {
        val withContainerNetwork = newJobRunSpec.command(networks = Seq(newNetwork.container()))
        validate(withContainerNetwork) should haveViolations(
          "/networks" -> JobRunSpecMessages.onlyContainersMayJoinContainerOrBridge
        )

        val withBridgeNetwork = newJobRunSpec.command(networks = Seq(newNetwork.bridge()))
        validate(withBridgeNetwork) should haveViolations(
          "/networks" -> JobRunSpecMessages.onlyContainersMayJoinContainerOrBridge
        )
      }

      "not require the specification of a host network, but also allow it" in {
        val withHostNetwork = newJobRunSpec.command(networks = Seq(newNetwork.host()))
        validate(withHostNetwork) should be(aSuccess)

        val withoutHostNetwork = JobRunSpec(cmd = Some("do the thing"), networks = Nil)
        validate(withoutHostNetwork) should be(aSuccess)
      }
    }

    "with docker containers" should {
      "allow the specification of a host, container/bridge or container network" in {
        val withContainerNetwork = newJobRunSpec.docker(networks = Seq(newNetwork.container()))
        validate(withContainerNetwork) should be(aSuccess)

        val withBridgeNetwork = newJobRunSpec.docker(networks = Seq(newNetwork.bridge()))

        validate(withBridgeNetwork) should be(aSuccess)

        val withHostNetwork = newJobRunSpec.docker(networks = Seq(newNetwork.host()))

        validate(withHostNetwork) should be(aSuccess)
      }

      "reject the specification of multiple container networks" in {
        val multipleContainerNetworks = newJobRunSpec.docker(
          networks = Seq(newNetwork.container(name = "network1"), newNetwork.container(name = "network2"))
        )

        validate(multipleContainerNetworks) should haveViolations(
          "/networks" -> JobRunSpecMessages.dockerDoesNotAllowSpecificationOfMultipleNetworks
        )
      }
    }

    "with UCR tasks" should {

      "allows the specification of a host, container/bridge or container network" in {
        val withContainerNetwork = newJobRunSpec.ucr(networks = Seq(newNetwork.container()))
        validate(withContainerNetwork) should be(aSuccess)

        val withBridgeNetwork = newJobRunSpec.ucr(networks = Seq(newNetwork.bridge()))

        validate(withBridgeNetwork) should be(aSuccess)

        val withHostNetwork = newJobRunSpec.ucr(networks = Seq(newNetwork.host()))

        validate(withHostNetwork) should be(aSuccess)
      }

      "permit the specification of multiple container networks" in {
        val multipleContainerNetworks = newJobRunSpec.ucr(
          networks = Seq(newNetwork.container(name = "network1"), newNetwork.container(name = "network2"))
        )

        validate(multipleContainerNetworks) should be(aSuccess)
      }
    }

    "validates that all container network names are unique" in {
      val multipleContainerNetworks = newJobRunSpec.ucr(
        networks = Seq(newNetwork.container(name = "network1"), newNetwork.container(name = "network1"))
      )

      validate(multipleContainerNetworks) should haveViolations(
        "/networks" -> JobRunSpecMessages.networkNamesMustBeUnique
      )
    }
  }
}

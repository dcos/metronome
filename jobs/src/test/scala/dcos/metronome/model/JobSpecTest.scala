package dcos.metronome
package model

import dcos.metronome.model.JobSpec.ValidationError
import dcos.metronome.utils.test.Mockito
import org.scalatest.{FunSuite, GivenWhenThen, Matchers}

class JobSpecTest extends FunSuite with Matchers with Mockito with GivenWhenThen {

  test("Job spec with no dependencies is valid") {
    val f = Fixture()
    val jobSpecA = JobSpec(id = JobId("a"), run = f.runSpec)

    JobSpec.validateDependencies(jobSpecA, Seq.empty) // does not throw
  }

  test("New job spec with dependencies is valid") {
    val f = Fixture()
    val jobSpecA = JobSpec(id = JobId("a"), run = f.runSpec)
    val jobSpecB = JobSpec(id = JobId("b"), run = f.runSpec, dependencies = Seq(jobSpecA.id))

    JobSpec.validateDependencies(jobSpecB, Seq(jobSpecA)) // does not throw
  }

  test("New job spec with an unknown dependency is invalid") {
    val f = Fixture()
    val jobSpecA = JobSpec(id = JobId("a"), run = f.runSpec)
    val jobSpecB = JobSpec(id = JobId("b"), run = f.runSpec, dependencies = Seq(JobId("unknown.job"), jobSpecA.id))

    the[ValidationError] thrownBy {
      JobSpec.validateDependencies(jobSpecB, Seq(jobSpecA))
    } should have message "Dependencies contain unknown jobs. unknown=[unknown.job]"
  }

  test("Updated job spec introduces a cycle and is invalid") {
    val f = Fixture()
    val jobSpecA = JobSpec(id = JobId("a"), run = f.runSpec)
    val jobSpecB = JobSpec(id = JobId("b"), run = f.runSpec, dependencies = Seq(jobSpecA.id))
    val updatedJobSpecA = jobSpecA.copy(dependencies = Seq(jobSpecB.id))

    the[ValidationError] thrownBy {
      JobSpec.validateDependencies(updatedJobSpecA, Seq(jobSpecA, jobSpecB))
    } should have message "Dependencies have a cycle."
  }

  case class Fixture() {
    val runSpec = JobRunSpec(cmd = Some("sleep 1000"))
  }
}

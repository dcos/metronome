package dcos.metronome
package model

import dcos.metronome.model.JobSpec.ValidationError
import dcos.metronome.utils.test.Mockito
import org.scalatest.{FunSuite, GivenWhenThen, Matchers}

class JobSpecTest extends FunSuite with Matchers with Mockito with GivenWhenThen with ValidationTestLike {

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

  test("a job with dependencies and a schedule is invalid") {
    val f = Fixture()
    val jobSpecA = JobSpec(id = JobId("a"), run = f.runSpec)
    val jobSpecB = JobSpec(
      id = JobId("b"),
      run = f.runSpec,
      dependencies = Seq(jobSpecA.id),
      schedules = Seq(Builders.newScheduleSpec())
    )

    JobSpec.validJobSpec(jobSpecB) should haveViolations(
      "/" -> "Jobs may not have both dependencies and schedules specified"
    )
  }

  case class Fixture() {
    val runSpec = Builders.newJobRunSpec.command()
  }
}

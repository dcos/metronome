package dcos.metronome.jobinfo.impl

import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.JobSpecSelector
import dcos.metronome.jobrun.JobRunServiceFixture
import dcos.metronome.jobspec.impl.JobSpecServiceFixture
import dcos.metronome.model.{ CronSpec, ScheduleSpec, JobSpec }
import mesosphere.marathon.state.PathId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, GivenWhenThen, FunSuite }
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.immutable._

class JobInfoServiceImplTest extends FunSuite with GivenWhenThen with ScalaFutures with Matchers {

  test("Fetch a specific JobSpec with no embed") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJob(f.spec1.id, JobSpecSelector.all, Set.empty).futureValue

    Then("No Job is returned")
    result1 should be(defined)
    result1.get.schedules should be(empty)
    result1.get.activeRuns should be(empty)
  }

  test("Fetch a specific JobSpec with activeRuns and schedules") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJob(f.spec1.id, JobSpecSelector.all, Set(Embed.ActiveRuns, Embed.Schedules)).futureValue

    Then("No Job is returned")
    result1 should be(defined)
    result1.get.schedules should be(defined)
    result1.get.activeRuns should be(defined)
  }

  test("Empty embed options will not render embedded info") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJobs(JobSpecSelector.all, Set.empty).futureValue

    Then("The extended info is empty")
    result1 should have size 2
    result1.foreach(_.schedules should be(empty))
    result1.foreach(_.activeRuns should be(empty))
  }

  test("A filter will filter results") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJobs(JobSpecSelector(_ => false), Set.empty).futureValue

    Then("No Job is returned")
    result1 should have size 0
  }

  test("ActiveRuns embed option will render embedded activeRuns") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJobs(JobSpecSelector.all, Set(Embed.ActiveRuns)).futureValue

    Then("The extended info is rendered for active runs")
    result1 should have size 2
    result1.foreach(_.schedules should be(empty))
    result1.foreach(_.activeRuns.get should have size 1)
  }

  test("Schedules embed option will render embedded schedules") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJobs(JobSpecSelector.all, Set(Embed.Schedules)).futureValue

    Then("The extended info is rendered for active runs")
    result1 should have size 2
    result1.foreach(_.schedules.get should have size 2)
    result1.foreach(_.activeRuns should be(empty))
  }

  class Fixture {
    val CronSpec(cron) = "* * * * *"
    val schedule1 = ScheduleSpec("id1", cron)
    val schedule2 = ScheduleSpec("id2", cron)

    val spec1 = JobSpec(PathId("spec1"), schedules = Seq(schedule1, schedule2))
    val spec2 = JobSpec(PathId("spec2"), schedules = Seq(schedule1, schedule2))

    val specService = JobSpecServiceFixture.simpleJobSpecService()
    val runService = JobRunServiceFixture.simpleJobRunService()

    specService.createJobSpec(spec1).futureValue
    specService.createJobSpec(spec2).futureValue
    runService.startJobRun(spec1).futureValue
    runService.startJobRun(spec2).futureValue

    val jobInfoService = new JobInfoServiceImpl(specService, runService)
  }
}

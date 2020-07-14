package dcos.metronome
package jobinfo.impl

import java.time.Clock

import dcos.metronome.history.JobHistoryServiceFixture
import dcos.metronome.jobinfo.JobInfo.Embed
import dcos.metronome.jobinfo.JobSpecSelector
import dcos.metronome.jobrun.JobRunServiceFixture
import dcos.metronome.jobspec.impl.JobSpecServiceFixture
import dcos.metronome.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class JobInfoServiceImplTest extends FunSuite with GivenWhenThen with ScalaFutures with Matchers {

  test("Fetch a specific JobSpec with no embed") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJob(f.spec1.id, JobSpecSelector.all, Set.empty).futureValue

    Then("No Job is returned")
    result1 should be(defined)
    result1.get.schedules should have size 2
    result1.get.activeRuns should be(empty)
  }

  test("Fetch a specific JobSpec with activeRuns and schedules") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 =
      f.jobInfoService.selectJob(f.spec1.id, JobSpecSelector.all, Set(Embed.ActiveRuns)).futureValue

    Then("No Job is returned")
    result1 should be(defined)
    result1.get.activeRuns should be(defined)
  }

  test("Empty embed options will only render schedules") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJobs(JobSpecSelector.all, Set.empty).futureValue

    Then("The extended info is empty")
    result1 should have size 2
    result1.foreach(_.schedules should not be (empty))
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
    result1.foreach(_.schedules should have size 2)
    result1.foreach(_.activeRuns.get should have size 1)
  }

  test("Schedules embed option will render embedded schedules") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result1 = f.jobInfoService.selectJobs(JobSpecSelector.all, Set.empty).futureValue

    Then("The extended info is rendered for active runs")
    result1 should have size 2
    result1.foreach(_.schedules should have size 2)
    result1.foreach(_.activeRuns should be(empty))
  }

  test("Status embed option will render embedded history info") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result = f.jobInfoService.selectJobs(JobSpecSelector.all, Set(Embed.History)).futureValue

    Then("The extended info is rendered for active runs")
    result should have size 2
    result.foreach(_.schedules should have size 2)
    result.foreach(_.activeRuns should be(empty))
    result.map(_.history.get).toSet should be(Set(f.history1, f.history2))
  }

  test("Status embed option will render embedded historyShort info") {
    Given("A repo with 2 specs and 2 runs")
    val f = new Fixture

    When("The job info is fetched")
    val result = f.jobInfoService.selectJobs(JobSpecSelector.all, Set(Embed.HistorySummary)).futureValue

    Then("The extended info is rendered for active runs")
    result should have size 2
    result.foreach(_.schedules should have size 2)
    result.foreach(_.activeRuns should be(empty))
    result.map(_.historySummary.get).toSet should be(Set(f.historySummary1, f.historySummary2))
  }

  class Fixture {
    val clock = new SettableClock(Clock.systemUTC())
    val CronSpec(cron) = "* * * * *"
    val schedule1 = ScheduleSpec("id1", cron)
    val schedule2 = ScheduleSpec("id2", cron)

    val spec1 = JobSpec(JobId("spec1"), schedules = Seq(schedule1, schedule2))
    val spec2 = JobSpec(JobId("spec2"), schedules = Seq(schedule1, schedule2))

    val history1 = JobHistory(spec1.id, 23, 23, Some(clock.instant()), Some(clock.instant()), Seq.empty, Seq.empty)
    val history2 = JobHistory(spec2.id, 23, 23, Some(clock.instant()), Some(clock.instant()), Seq.empty, Seq.empty)
    val historySummary1 = JobHistorySummary(history1)
    val historySummary2 = JobHistorySummary(history2)

    val specService = JobSpecServiceFixture.simpleJobSpecService()
    val runService = JobRunServiceFixture.simpleJobRunService()
    val historyService = JobHistoryServiceFixture.simpleHistoryService(Seq(history1, history2))

    specService.createJobSpec(spec1).futureValue
    specService.createJobSpec(spec2).futureValue
    runService.startJobRun(spec1).futureValue
    runService.startJobRun(spec2).futureValue

    val jobInfoService = new JobInfoServiceImpl(specService, runService, historyService)
  }
}

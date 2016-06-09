package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ MockApiComponents, OneAppPerSuiteWithComponents, UnknownJob }
import dcos.metronome.model.{ CronSpec, JobSpec, ScheduleSpec }
import mesosphere.marathon.state.PathId
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import dcos.metronome.api.v1.models.{ JobSpecFormat => _, _ }

class ScheduledJobSpecControllerTest extends PlaySpec with OneAppPerSuiteWithComponents[MockApiComponents] {

  import ScheduledJobSpecController.JobSpecWithScheduleFormat

  "POST /scheduled-jobs" should {
    "creates a job when sending a valid job spec" in {
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "ignore given schedules when sending a valid job spec with schedules" in {
      val jobSpecWithSchedule = Json.toJson(jobSpec2.copy(schedules = Seq(schedule1)))
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpecWithSchedule)).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec2Json
    }

    "indicate a problem when sending invalid json" in {
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(invalid)).get
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }
  }

  "PUT /scheduled-jobs/{id}" should {
    "update a specific existing job" in {
      val update = jobSpec1.copy(schedules = Seq(schedule2))
      val updateJson = Json.toJson(update)
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(updateJson)).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe updateJson
    }

    "give a 404 for a non existing job" in {
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/notexistent").withJsonBody(jobSpec1Json)).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }

    "indicate a problem when sending invalid json" in {
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(invalid)).get
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }
  }

  val CronSpec(cron) = "* * * * *"
  val schedule1 = ScheduleSpec("id1", cron)
  val schedule2 = ScheduleSpec("id2", cron)
  val jobSpec1 = JobSpec(PathId("spec1"), "spec1", schedules = Seq(schedule1))
  val jobSpec1Json = Json.toJson(jobSpec1)
  val jobSpec2 = JobSpec(PathId("spec2"), "spec2", schedules = Seq(schedule1))
  val jobSpec2Json = Json.toJson(jobSpec2)

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}


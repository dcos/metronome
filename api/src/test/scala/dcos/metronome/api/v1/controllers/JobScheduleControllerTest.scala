package dcos.metronome.api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ UnknownSchedule, MockApiComponents, OneAppPerSuiteWithComponents, UnknownJob }
import dcos.metronome.model.{ CronSpec, JobSpec, ScheduleSpec }
import mesosphere.marathon.state.PathId
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobScheduleControllerTest extends PlaySpec with OneAppPerSuiteWithComponents[MockApiComponents] with BeforeAndAfterAll {

  "POST /jobs/{id}/schedules" should {
    "create a job schedule when sending a valid schedule spec" in {
      val response = route(app, FakeRequest(POST, s"/jobs/$specId/schedules").withJsonBody(schedule1Json)).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe schedule1Json
    }

    "not found if the job id is not known" in {
      val response = route(app, FakeRequest(POST, s"/jobs/notexistent/schedules").withJsonBody(schedule1Json)).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }

    "indicate a problem when sending invalid json" in {
      val invalid = schedule2Json.as[JsObject] ++ Json.obj("cron" -> "wrong cron")
      val response = route(app, FakeRequest(POST, s"/jobs/$specId/schedules").withJsonBody(invalid)).get
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "message").as[String] must include ("Json validation error")
    }
  }

  "GET /jobs/{id}/schedules" should {
    "get all available job schedules" in {
      val response = route(app, FakeRequest(GET, s"/jobs/$specId/schedules")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.toSet mustBe Set(schedule1Json)
    }
  }

  "GET /jobs/{id}/schedules/{scheduleId}" should {
    "return a specific existing schedule" in {
      val response = route(app, FakeRequest(GET, s"/jobs/$specId/schedules/${schedule1.id}")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe schedule1Json
    }

    "give a 404 for a non existing schedule" in {
      val response = route(app, FakeRequest(GET, s"/jobs/$specId/schedules/notexistent")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownSchedule("notexistent"))
    }
  }

  "PUT /jobs/{id}/schedules/{scheduleId}" should {
    "update a specific existing schedule" in {
      val update = schedule1.copy(cron = cron2)
      val updateJson = Json.toJson(update)
      val response = route(app, FakeRequest(PUT, s"/jobs/$specId/schedules/${update.id}").withJsonBody(updateJson)).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe updateJson
    }

    "give a 404 for a non existing job" in {
      val response = route(app, FakeRequest(PUT, s"/jobs/$specId/schedules/notexistent").withJsonBody(schedule1Json)).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownSchedule("notexistent"))
    }

    "indicate a problem when sending invalid json" in {
      val invalid = schedule1Json.as[JsObject] ++ Json.obj("cron" -> "no valid cron")
      val response = route(app, FakeRequest(PUT, s"/jobs/$specId/schedules/${schedule1.id}").withJsonBody(invalid)).get
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "message").as[String] must include ("Json validation error")
    }
  }

  "DELETE /jobs/{id}/schedules/{scheduleId}" should {
    "delete a specific existing schedule" in {
      val response = route(app, FakeRequest(DELETE, s"/jobs/$specId/schedules/${schedule1.id}")).get
      status(response) mustBe OK
    }

    "give a 404 for a non existing schedule" in {
      val response = route(app, FakeRequest(DELETE, s"/jobs/$specId/schedules/notexistent")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownSchedule("notexistent"))
    }
  }

  override protected def beforeAll(): Unit = {
    val response = route(app, FakeRequest(POST, "/jobs").withJsonBody(jobSpecJson)).get
    status(response) mustBe CREATED
    contentType(response) mustBe Some("application/json")
    contentAsJson(response) mustBe jobSpecJson
  }

  val CronSpec(cron1) = "* * * * *"
  val CronSpec(cron2) = "1 2 3 4 5"
  val schedule1 = ScheduleSpec("id1", cron1)
  val schedule2 = ScheduleSpec("id2", cron2)
  val schedule1Json = Json.toJson(schedule1)
  val schedule2Json = Json.toJson(schedule2)
  val specId = PathId("spec")
  val jobSpec = JobSpec(specId, "description")
  val jobSpecJson = Json.toJson(jobSpec)

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}


package dcos.metronome.api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ MockApiComponents, OneAppPerSuiteWithComponents, UnknownJob }
import dcos.metronome.model.{ CronSpec, JobSpec, ScheduleSpec }
import mesosphere.marathon.state.PathId
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobSpecControllerTest extends PlaySpec with OneAppPerSuiteWithComponents[MockApiComponents] {

  "POST /jobs" should {
    "creates a job when sending a valid job spec" in {
      val response = route(app, FakeRequest(POST, "/jobs").withJsonBody(jobSpec1Json)).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "ignore given schedules when sending a valid job spec with schedules" in {
      val jobSpecWithSchedule = Json.toJson(jobSpec2.copy(schedules = Seq(schedule1)))
      val response = route(app, FakeRequest(POST, "/jobs").withJsonBody(jobSpecWithSchedule)).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec2Json
    }

    "indicate a problem when sending invalid json" in {
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(POST, "/jobs").withJsonBody(invalid)).get
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "indicate a problem when creating an existing job" in {
      val response = route(app, FakeRequest(POST, "/jobs").withJsonBody(jobSpec1Json)).get
      status(response) mustBe CONFLICT
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Job with this id already exists"))
    }
  }

  "GET /jobs" should {
    "get all available jobs" in {
      val response = route(app, FakeRequest(GET, "/jobs")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.toSet mustBe Set(jobSpec1Json, jobSpec2Json)
    }
  }

  "GET /jobs/{id}" should {
    "return a specific existing job" in {
      val response = route(app, FakeRequest(GET, s"/jobs/${jobSpec1.id}")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "give a 404 for a non existing job" in {
      val response = route(app, FakeRequest(GET, s"/jobs/notexistent")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }
  }

  "PUT /jobs/{id}" should {
    "update a specific existing job" in {
      val update = jobSpec1.copy(labels = Map("a" -> "b"))
      val updateJson = Json.toJson(update)
      val response = route(app, FakeRequest(PUT, s"/jobs/${jobSpec1.id}").withJsonBody(updateJson)).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe updateJson
    }

    "give a 404 for a non existing job" in {
      val response = route(app, FakeRequest(PUT, s"/jobs/notexistent").withJsonBody(jobSpec1Json)).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }

    "indicate a problem when sending invalid json" in {
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(PUT, s"/jobs/${jobSpec1.id}").withJsonBody(invalid)).get
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }
  }

  "DELETE /jobs/{id}" should {
    "delete a specific existing job" in {
      val response = route(app, FakeRequest(DELETE, s"/jobs/${jobSpec2.id}")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec2Json
    }

    "give a 404 for a non existing job" in {
      val response = route(app, FakeRequest(DELETE, s"/jobs/notexistent")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }
  }

  def spec(id: String) = JobSpec(PathId(id), "description")
  val CronSpec(cron) = "* * * * *"
  val schedule1 = ScheduleSpec("id1", cron)
  val jobSpec1 = spec("spec1")
  val jobSpec1Json = Json.toJson(jobSpec1)
  val jobSpec2 = spec("spec2")
  val jobSpec2Json = Json.toJson(jobSpec2)

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}


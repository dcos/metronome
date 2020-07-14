package dcos.metronome
package api.v1.controllers

import java.time.{Clock, Instant, ZoneOffset}

import dcos.metronome.api._
import dcos.metronome.api.v1.models._
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.jobspec.impl.JobSpecServiceFixture
import dcos.metronome.model._
import mesosphere.marathon.core.plugin.PluginManager
import org.scalatest.{BeforeAndAfter, GivenWhenThen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfter, GivenWhenThen}
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobScheduleControllerTest
    extends PlaySpec
    with OneAppPerTestWithComponents[MockApiComponents]
    with ScalaFutures
    with GivenWhenThen
    with BeforeAndAfter {

  "POST /jobs/{id}/schedules" should {

    "create a job schedule when sending a valid schedule spec" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A Schedule is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get

      Then("The schedule is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe schedule1Json

      And("The spec must include the new schedule")
      val fetchedSpec = route(app, FakeRequest(GET, s"/v1/jobs/$specId")).get
      (contentAsJson(fetchedSpec) \ "schedules" \ 0).get mustBe schedule1Json
    }

    "create a job schedule with timezone" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A Schedule is created")
      val scheduleJson = Json.parse("""{
                                      |    "id": "everyminute",
                                      |    "cron": "* * * * *",
                                      |    "concurrencyPolicy": "ALLOW",
                                      |    "enabled": true,
                                      |    "startingDeadlineSeconds": 60,
                                      |    "timezone": "America/Chicago"
                                      |  }""".stripMargin)
      val response = route(
        app,
        FakeRequest(POST, s"/v1/jobs/$specId/schedules")
          .withJsonBody(scheduleJson)
      ).get

      Then("The schedule is created")
      (contentAsJson(response) \ "timezone").as[String] mustBe "America/Chicago"
    }

    "create a job schedule using the forbid concurrencyPolicy" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A Schedule is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule3Json)).get

      Then("The schedule is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe schedule3Json
    }

    "cannot create a job schedule with the same id" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("A Schedule is created with the same id")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get

      Then("The schedule is created")
      status(response) mustBe CONFLICT
      contentType(response) mustBe Some("application/json")
      contentAsString(response) must include("A schedule with id id1 already exists")
    }

    "cannot create more than one job schedule per job (only temporary limitation)" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("A Schedule is created with the same id")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule2Json)).get

      Then("The schedule is created")
      status(response) mustBe CONFLICT
      contentType(response) mustBe Some("application/json")
      contentAsString(response) must include("Only one schedule supported at the moment")
    }

    "not found if the job id is not known" in {
      Given("No job")

      When("A schedule is added to a non existing job")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/notexistent/schedules").withJsonBody(schedule1Json)).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(JobId("notexistent")))
    }

    "indicate a problem when sending invalid json" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("Invalid schedule is sent")
      val invalid = schedule2Json.as[JsObject] ++ Json.obj("cron" -> "wrong cron")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(invalid)).get

      Then("A validation problem is indicated")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "message").as[String] mustBe "Object is not valid"
    }

    "without auth this endpoint is not accessible" in {
      Given("An existing job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }

    // regression of METRONOME-236
    "end with validation error for invalid cron" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("Invalid schedule is sent")
      val invalid = schedule2Json.as[JsObject] ++ Json.obj("cron" -> "0 0 31 2 *")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(invalid)).get

      Then("A validation problem is indicated")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "message").as[String] mustBe "Object is not valid"
    }
  }

  "GET /jobs/{id}/schedules" should {
    "get all available job schedules" in {
      Given("A job with a schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("THe schedule is queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules")).get

      Then("The schedule is returned")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.toSet mustBe Set(schedule1Json)
    }

    "without auth this endpoint is not accessible" in {
      Given("An existing job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules")).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "GET /jobs/{id}/schedules/{scheduleId}" should {
    "return a specific existing schedule" in {
      Given("A job with a schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("A specific schedule is queried.")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules/${schedule1.id}")).get

      Then("The specific schedule is returned")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe schedule1Json
    }

    "give a 404 for a non existing schedule" in {
      Given("A job with no schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A non existent schedule is queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules/notexistent")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownSchedule("notexistent"))
    }

    "without auth this endpoint is not accessible" in {
      Given("An job with a schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules/${schedule1.id}")).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(GET, s"/v1/jobs/$specId/schedules/${schedule1.id}")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "PUT /jobs/{id}/schedules/{scheduleId}" should {
    "update a specific existing schedule" in {
      Given("A job with an existing schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("The schedule is updated")
      val update = schedule1.copy(cron = cron2)
      val updateJson = Json.toJson(update)
      val response =
        route(app, FakeRequest(PUT, s"/v1/jobs/$specId/schedules/${update.id}").withJsonBody(updateJson)).get

      Then("The schedule is updated")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe updateJson
    }

    "update a specific existing schedule wit a different id" in {
      Given("A job with an existing schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("The schedule is updated")
      val sendJson = Json.toJson(schedule1.copy(id = "ignore.me", cron = cron2))
      val response =
        route(app, FakeRequest(PUT, s"/v1/jobs/$specId/schedules/${schedule1.id}").withJsonBody(sendJson)).get

      Then("A validation problem is indicated")
      status(
        response
      ) mustBe UNPROCESSABLE_ENTITY //as long as we support only one schedule this will fail, update if we change this restriction
    }

    "give a 404 for a non existing schedule" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A non existing job us")
      val response =
        route(app, FakeRequest(PUT, s"/v1/jobs/$specId/schedules/notexistent").withJsonBody(schedule1Json)).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownSchedule("notexistent"))
    }

    "indicate a problem when sending invalid json" in {
      Given("A job with an existing schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("A schesule is updated with invalid json")
      val invalid = schedule1Json.as[JsObject] ++ Json.obj("cron" -> "no valid cron")
      val response =
        route(app, FakeRequest(PUT, s"/v1/jobs/$specId/schedules/${schedule1.id}").withJsonBody(invalid)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "message").as[String] mustBe "Object is not valid"
    }

    "without auth this endpoint is not accessible" in {
      Given("An job ")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden =
        route(app, FakeRequest(PUT, s"/v1/jobs/$specId/schedules/notexistent").withJsonBody(schedule1Json)).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized =
        route(app, FakeRequest(PUT, s"/v1/jobs/$specId/schedules/notexistent").withJsonBody(schedule1Json)).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "DELETE /jobs/{id}/schedules/{scheduleId}" should {
    "delete a specific existing schedule" in {
      Given("A job with a schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("The schedule is deleted")
      val response = route(app, FakeRequest(DELETE, s"/v1/jobs/$specId/schedules/${schedule1.id}")).get

      Then("The schedule is deleted")
      status(response) mustBe OK
    }

    "give a 404 for a non existing schedule" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A non existent schedule is deleted")
      val response = route(app, FakeRequest(DELETE, s"/v1/jobs/$specId/schedules/notexistent")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownSchedule("notexistent"))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job with a schedule")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/schedules").withJsonBody(schedule1Json)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(DELETE, s"/v1/jobs/$specId/schedules/${schedule1.id}")).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(DELETE, s"/v1/jobs/$specId/schedules/${schedule1.id}")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  val mockedClock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  val CronSpec(cron1) = "* * * * *"
  val CronSpec(cron2) = "1 2 3 4 5"
  val schedule1 = new ScheduleSpec("id1", cron1) {
    override def clock = mockedClock
  }
  val schedule2 = new ScheduleSpec("id2", cron2) {
    override def clock = mockedClock
  }
  val scheduleForbid = new ScheduleSpec(
    "id3",
    cron1,
    ScheduleSpec.DefaultTimeZone,
    ScheduleSpec.DefaultStartingDeadline,
    ConcurrencyPolicy.Forbid
  ) {
    override def clock = mockedClock
  }
  val schedule1Json = Json.toJson(schedule1)
  val schedule2Json = Json.toJson(schedule2)
  val schedule3Json = Json.toJson(scheduleForbid)
  val specId = JobId("spec")
  val jobSpec = JobSpec(specId, run = JobRunSpec(cmd = Some("cmd")))
  val jobSpecJson = Json.toJson(jobSpec)
  val auth = new TestAuthFixture

  before {
    auth.authorized = true
    auth.authenticated = true
  }

  override def createComponents(context: Context): MockApiComponents =
    new MockApiComponents(context) {
      override lazy val pluginManager: PluginManager = auth.pluginManager
      override lazy val jobSpecService: JobSpecService = JobSpecServiceFixture.simpleJobSpecService(mockedClock)
    }
}

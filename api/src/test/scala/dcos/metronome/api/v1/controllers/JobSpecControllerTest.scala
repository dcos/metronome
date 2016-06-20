package dcos.metronome.api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ TestAuthFixture, MockApiComponents, OneAppPerTestWithComponents, UnknownJob }
import dcos.metronome.model.{ JobRunSpec, CronSpec, JobSpec, ScheduleSpec }
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.state.PathId
import org.scalatest.{ BeforeAndAfter, GivenWhenThen }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.collection.immutable._

class JobSpecControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] with GivenWhenThen with ScalaFutures with BeforeAndAfter {

  "POST /jobs" should {
    "creates a job when sending a valid job spec" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get

      Then("The job is created")
      println(jobSpec1Json)
      println(contentAsJson(response))
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "ignore given schedules when sending a valid job spec with schedules" in {
      Given("No job")

      When("A job spec with schedules is sent")
      val jobSpecWithSchedule = Json.toJson(jobSpec2.copy(schedules = Seq(schedule1)))

      Then("The schedules get ignored")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSchedule)).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec2Json
    }

    "indicate a problem when sending invalid json" in {
      Given("No job")

      When("An invalid json is sent")
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(invalid)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "indicate a problem when creating an existing job" in {
      Given("An existing job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("An existing job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get

      Then("A conflict is sent")
      status(response) mustBe CONFLICT
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Job with this id already exists"))
    }

    "without auth this endpoint is not accessible" in {
      Given("No job")

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "GET /jobs" should {
    "get all available jobs" in {
      Given("Two job specs")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec2Json)).get.futureValue

      When("All jobspecs are queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs")).get

      Then("All job specs are returned")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.toSet mustBe Set(jobSpec1Json, jobSpec2Json)
    }

    "without auth this endpoint is not accessible" in {
      Given("Two job specs")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec2Json)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val filtered = route(app, FakeRequest(GET, s"/v1/jobs")).get

      Then("an empty list is send")
      status(filtered) mustBe OK
      contentAsJson(filtered).as[JsArray].value.size mustBe 0

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(GET, s"/v1/jobs")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "GET /jobs/{id}" should {
    "return a specific existing job" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("A specific job spec is queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/${jobSpec1.id}")).get

      Then("A specific job is returned")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "give a 404 for a non existing job" in {
      Given("No job")

      When("A non existing job is queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/notexistent")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(GET, s"/v1/jobs/${jobSpec1.id}")).get

      Then("a 404 response is send")
      status(forbidden) mustBe NOT_FOUND

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(GET, s"/v1/jobs/${jobSpec1.id}")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "PUT /jobs/{id}" should {
    "update a specific existing job" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("The job gets updated")
      val update = jobSpec1.copy(labels = Map("a" -> "b"))
      val updateJson = Json.toJson(update)
      val response = route(app, FakeRequest(PUT, s"/v1/jobs/${jobSpec1.id}").withJsonBody(updateJson)).get

      Then("The job is updated")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe updateJson
    }

    "give a 404 for a non existing job" in {
      Given("No job")

      When("A non existing job is updated")
      val response = route(app, FakeRequest(PUT, s"/v1/jobs/notexistent").withJsonBody(jobSpec1Json)).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }

    "indicate a problem when sending invalid json" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("An invalid json is sent")
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(PUT, s"/v1/jobs/${jobSpec1.id}").withJsonBody(invalid)).get

      Then("A validation problem is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue
      val updateJson = Json.toJson(jobSpec1.copy(labels = Map("a" -> "b")))

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(PUT, s"/v1/jobs/${jobSpec1.id}").withJsonBody(updateJson)).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(PUT, s"/v1/jobs/${jobSpec1.id}").withJsonBody(updateJson)).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "DELETE /jobs/{id}" should {
    "delete a specific existing job" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("An existing job is deleted")
      val response = route(app, FakeRequest(DELETE, s"/v1/jobs/${jobSpec1.id}")).get

      Then("The job is deleted")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "give a 404 for a non existing job" in {
      Given("No job")

      When("A non existent job is deleted")
      val response = route(app, FakeRequest(DELETE, s"/v1/jobs/notexistent")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(DELETE, s"/v1/jobs/${jobSpec1.id}")).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(DELETE, s"/v1/jobs/${jobSpec1.id}")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  def spec(id: String) = JobSpec(PathId(id), run = JobRunSpec(cmd = Some("test")))
  val CronSpec(cron) = "* * * * *"
  val schedule1 = ScheduleSpec("id1", cron)
  val jobSpec1 = spec("spec1")
  val jobSpec1Json = Json.toJson(jobSpec1)
  val jobSpec2 = spec("spec2")
  val jobSpec2Json = Json.toJson(jobSpec2)
  val auth = new TestAuthFixture

  before {
    auth.authorized = true
    auth.authenticated = true
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context) {
    override lazy val pluginManager: PluginManager = auth.pluginManager
  }
}


package dcos.metronome
package api.v1.controllers

import dcos.metronome.api._
import dcos.metronome.api.v1.models._
import dcos.metronome.model.{JobId, JobRunSpec, JobRunStatus, JobSpec}
import mesosphere.marathon.core.plugin.PluginManager
import org.scalatest.{BeforeAndAfter, GivenWhenThen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobRunControllerTest
    extends PlaySpec
    with OneAppPerTestWithComponents[MockApiComponents]
    with ScalaFutures
    with GivenWhenThen
    with BeforeAndAfter {
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  "POST /jobs/{id}/runs" should {
    "create a job run when posting to the runs endpoint" in {
      Given("An existing app")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("The request is sent")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get

      Then("The job run is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "status").as[JobRunStatus] mustBe JobRunStatus.Active
    }

    "not found if the job id is not known" in {
      Given("Empty Jobs")

      When("A non existent job run is queried")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/notexistent/runs")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(JobId("notexistent")))
    }

    "without auth this endpoint is not accessible" in {
      Given("An existing job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "GET /jobs/{id}/runs" should {
    "get all available job runs" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get.futureValue

      When("All jobs get listed")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get

      Then("The jobs are listed")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.size mustBe 1
    }

    "without auth this endpoint is not accessible" in {
      Given("no auth")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get.futureValue

      When("we do a request without authorization")
      auth.authorized = false
      val all = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get

      Then("The response array is empty")
      status(all) mustBe OK
      contentAsJson(all).as[JsArray].value.size mustBe 0

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }

    "responses auth in runs show up in request for specific instance" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      val run = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(run) \ "id").as[String]

      When("A specific run is requested")
      val instanceResponse = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get
      val runsResponse = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get

      Then("A specific run is returned")
      status(instanceResponse) mustBe OK
      contentType(instanceResponse) mustBe Some("application/json")
      (contentAsJson(instanceResponse) \ "id").as[String] mustBe runId

      status(runsResponse) mustBe OK
      contentType(runsResponse) mustBe Some("application/json")
      contentAsJson(runsResponse).as[JsArray].value.size mustBe 1
    }

    "responses not auth in runs do not show up in request for specific instance" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      val run = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(run) \ "id").as[String]

      When("A specific run is requested")
      auth.authorized = false
      val instanceResponse = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get
      val runsResponse = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get

      Then("A specific run is returned")
      status(instanceResponse) mustBe UNAUTHORIZED

      status(runsResponse) mustBe OK
      contentType(runsResponse) mustBe Some("application/json")
      contentAsJson(runsResponse).as[JsArray].value.size mustBe 0
    }
  }

  "GET /jobs/{id}/runs/{runId}" should {
    "return a specific existing jobRun" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      val run = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(run) \ "id").as[String]

      When("A specific run is requested")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get

      Then("A specific run is returned")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
    }

    "give a 404 for a non existing jobRun" in {
      Given("A job with no run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A non existing run is queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/notexistent")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJobRun(specId, "notexistent"))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      val run = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(run) \ "id").as[String]

      When("we do a request without authorization")
      auth.authorized = false
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get

      Then("a unauthorized response is send")
      status(response) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "POST /jobs/{id}/runs/{runId}/actions/stop" should {
    "delete a specific existing jobRun" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      val run = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(run) \ "id").as[String]

      When("The run is stopped")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs/$runId/actions/stop")).get

      Then("The run is stopped")
      status(response) mustBe OK
    }

    "give a 404 for a non existing jobRun" in {
      Given("A job")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A non existing job is deleted")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs/notexistent/actions/stop")).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJobRun(specId, "notexistent"))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      val run = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(run) \ "id").as[String]

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs/$runId/actions/stop")).get

      Then("a unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs/$runId/actions/stop")).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  val auth = new TestAuthFixture
  val specId = JobId("spec")
  val jobSpec = JobSpec(specId, run = JobRunSpec(cmd = Some("cmd")))
  val jobSpecJson = Json.toJson(jobSpec)

  before {
    auth.authorized = true
    auth.authenticated = true
  }

  override def createComponents(context: Context): MockApiComponents =
    new MockApiComponents(context) {
      override lazy val pluginManager: PluginManager = auth.pluginManager
    }
}

package dcos.metronome.api.v1.controllers

import dcos.metronome.api._
import dcos.metronome.api.v1.models._
import dcos.metronome.model.{JobRunStatus, JobSpec}
import mesosphere.marathon.state.PathId
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobRunControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] with ScalaFutures with GivenWhenThen {

  "POST /jobs/{id}/runs" should {
    "create a job run when posting to the runs endpoint" in {
      Given("An existing app")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("The request is send")
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

      Then("A 404 is send")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
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
  }

  "GET /jobs/{id}/runs/{runId}" should {
    "return a specific existing jobRun" in {
      Given("A job with a run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get.futureValue

      When("A specific run is requested")
      val all = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get

      Then("A specific run is returned")
      val runId = (contentAsJson(all).as[JsArray].value.head \ "id").as[String]
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
    }

    "give a 404 for a non existing jobRun" in {
      Given("A job with no run")
      route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get.futureValue

      When("A non existing run is queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/notexistent")).get

      Then("A 404 is send")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJobRun(specId, "notexistent"))
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

      Then("A 404 is send")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJobRun(specId, "notexistent"))
    }
  }

  val specId = PathId("spec")
  val jobSpec = JobSpec(specId, "description")
  val jobSpecJson = Json.toJson(jobSpec)

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}


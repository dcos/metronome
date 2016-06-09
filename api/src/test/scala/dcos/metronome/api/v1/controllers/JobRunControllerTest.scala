package dcos.metronome.api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api.{ UnknownJobRun, MockApiComponents, OneAppPerSuiteWithComponents, UnknownJob }
import dcos.metronome.model.{ JobRunStatus, JobSpec }
import mesosphere.marathon.state.PathId
import org.scalatest.{ BeforeAndAfterAll, Matchers }
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobRunControllerTest extends PlaySpec with OneAppPerSuiteWithComponents[MockApiComponents] with BeforeAndAfterAll {

  "POST /jobs/{id}/runs" should {
    "create a job run when posting to the runs endpoint" in {
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs")).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      (contentAsJson(response) \ "status").as[JobRunStatus] mustBe JobRunStatus.Active
    }

    "not found if the job id is not known" in {
      val response = route(app, FakeRequest(POST, s"/v1/jobs/notexistent/runs")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(PathId("notexistent")))
    }
  }

  "GET /jobs/{id}/runs" should {
    "get all available job runs" in {
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.size mustBe 1
    }
  }

  "GET /jobs/{id}/runs/{runId}" should {
    "return a specific existing jobRun" in {
      val all = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(all).as[JsArray].value.head \ "id").as[String]
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/$runId")).get
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
    }

    "give a 404 for a non existing jobRun" in {
      val response = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs/notexistent")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJobRun(specId, "notexistent"))
    }
  }

  "POST /jobs/{id}/runs/{runId}/actions/stop" should {
    "delete a specific existing jobRun" in {
      val all = route(app, FakeRequest(GET, s"/v1/jobs/$specId/runs")).get
      val runId = (contentAsJson(all).as[JsArray].value.head \ "id").as[String]
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs/$runId/actions/stop")).get
      status(response) mustBe OK
    }

    "give a 404 for a non existing jobRun" in {
      val response = route(app, FakeRequest(POST, s"/v1/jobs/$specId/runs/notexistent/actions/stop")).get
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJobRun(specId, "notexistent"))
    }
  }

  override protected def beforeAll(): Unit = {
    val response = route(app, FakeRequest(POST, "/v1/jobs").withJsonBody(jobSpecJson)).get
    status(response) mustBe CREATED
    contentType(response) mustBe Some("application/json")
    contentAsJson(response) mustBe jobSpecJson
  }

  val specId = PathId("spec")
  val jobSpec = JobSpec(specId, "description")
  val jobSpecJson = Json.toJson(jobSpec)

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context)
}


package dcos.metronome
package api.v0.controllers

import dcos.metronome.api.v1.models.{ JobSpecFormat => _, _ }
import dcos.metronome.api.{ MockApiComponents, OneAppPerTestWithComponents, TestAuthFixture, UnknownJob }
import dcos.metronome.model._
import mesosphere.marathon.core.plugin.PluginManager
import org.scalatest.{ BeforeAndAfter, GivenWhenThen }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ScheduledJobSpecControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] with GivenWhenThen with ScalaFutures with BeforeAndAfter {

  import ScheduledJobSpecController._
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  "POST /scheduled-jobs" should {
    "creates a job when sending a valid job spec" in {
      Given("No Job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "ignore given schedules when sending a valid job spec with schedules" in {
      Given("No Job")

      When("A job with a schedule is created")
      val jobSpecWithSchedule = Json.toJson(jobSpec2.copy(schedules = Seq(schedule1)))
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpecWithSchedule)).get

      Then("The job is created with schedule")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec2Json
    }

    "indicate a problem when sending invalid json" in {
      Given("No job")
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")

      When("A job with invalid json is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(invalid)).get

      Then("A validation problem is sent")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "fail for invalid startingDeadline" in {
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")

      When("A job with invalid json is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(invalid)).get

      Then("A validation problem is sent")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "without auth this endpoint is not accessible" in {
      Given("No job")

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "PUT /scheduled-jobs/{id}" should {
    "update a specific existing job" in {
      Given("A job with schedule")
      route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("An existing job is updated")
      val update = jobSpec1.copy(schedules = Seq(schedule2))
      val updateJson = Json.toJson(update)
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(updateJson)).get

      Then("The job is updated")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe updateJson
    }

    "update a specific existing job with a different id" in {
      Given("A job with schedule")
      route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("An existing job is updated")
      val expectedJson = Json.toJson(jobSpec1.copy(schedules = Seq(schedule2)))
      val sendJson = Json.toJson(jobSpec1.copy(id = JobId("ignore.me"), schedules = Seq(schedule2)))
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(sendJson)).get

      Then("The job is updated")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe expectedJson
    }

    "give a 404 for a non existing job" in {
      Given("No job")

      When("A non existing job is updated")
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/notexistent").withJsonBody(jobSpec1Json)).get

      Then("A 404 is returned")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(JobId("notexistent")))
    }

    "indicate a problem when sending invalid json" in {
      Given("A job with schedule")
      route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("An invalid update is performed")
      val invalid = jobSpec1Json.as[JsObject] ++ Json.obj("id" -> "/not/valid")
      val response = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(invalid)).get

      Then("A validation problem is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "without auth this endpoint is not accessible" in {
      Given("A job with schedule")
      route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(jobSpec1Json)).get.futureValue
      val update = jobSpec1.copy(schedules = Seq(schedule2))
      val updateJson = Json.toJson(update)

      When("we do a request without authorization")
      auth.authorized = false
      val forbidden = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(updateJson)).get

      Then("an unauthorized response is send")
      status(forbidden) mustBe UNAUTHORIZED

      When("we do a request without authentication")
      auth.authenticated = false
      val unauthorized = route(app, FakeRequest(PUT, s"/v0/scheduled-jobs/${jobSpec1.id}").withJsonBody(updateJson)).get

      Then("a forbidden response is send")
      status(unauthorized) mustBe FORBIDDEN
    }
  }

  "Artifacts and user are correctly deserialized" in {
    Given("a json with artifacts")
    val json = Json.parse(
      """
        |{
        |  "id": "test-user-override",
        |  "run": {
        |    "artifacts": [
        |      {
        |        "uri": "https://dcos.io/assets/images/logos/mesosphere.svg"
        |      }
        |    ],
        |    "cmd": "whoami && printf 'iamme' | tee file && sleep 1000",
        |    "cpus": 0.01,
        |    "mem": 32,
        |    "disk": 0,
        |    "user": "nobody"
        |  }
        |}
      """.stripMargin)
    val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(json)).get

    Then("The job is created")
    status(response) mustBe CREATED
    contentType(response) mustBe Some("application/json")

    val jobSpec = contentAsJson(response).as[JobSpec]
    jobSpec.run.artifacts.head.uri mustBe "https://dcos.io/assets/images/logos/mesosphere.svg"
    jobSpec.run.user mustBe Some("nobody")
  }

  val CronSpec(cron) = "* * * * *"
  val schedule1 = ScheduleSpec("id1", cron)
  val schedule2 = ScheduleSpec("id2", cron)
  val jobSpec1 = JobSpec(JobId("spec1"), schedules = Seq(schedule1), run = JobRunSpec(cmd = Some("cmd")))
  val jobSpec1Json = Json.toJson(jobSpec1)
  val jobSpec2 = JobSpec(JobId("spec2"), schedules = Seq(schedule1), run = JobRunSpec(cmd = Some("cmd")))
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

package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api._
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

class JobSpecControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] with GivenWhenThen with ScalaFutures with BeforeAndAfter {

  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  "POST /jobs" should {
    "creates a job when sending a valid job spec" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpec1Json
    }

    "creates job when sending docker spec without forcePullImage property" in {
      Given("Job spec without forcePullImage")
      val specJson = """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"docker":{"image":"image"}}}"""

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(Json.parse(specJson))).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
    }

    "creates job when sending docker spec with forcePullImage property against v0" in {
      Given("Job spec with forcePullImage")
      val specJson = """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"docker":{"image":"image","forcePullImage":true}}}"""

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(Json.parse(specJson))).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
    }

    "creates job when sending docker spec without forcePullImage property against v0" in {
      Given("Job spec without forcePullImage")
      val specJson = """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"docker":{"image":"image"}}}"""

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(Json.parse(specJson))).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
    }

    "creates job when sending forbid concurrency property against v0" in {
      Given("Job spec with a schedule containing a forbid concurrency policy")
      val specJson =
        """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"docker":{"image":"image"}}, "schedules": [
          |{"id": "default","enabled": true,"cron": "* * * * *","timezone": "UTC","concurrencyPolicy": "FORBID"}
          |]}""".stripMargin

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(Json.parse(specJson))).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
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

    "create a job with secrets" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSecretsJson)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobSpecWithSecretsJson
    }

    "indicate a problem when creating a job without a secret definition" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSecretVarsOnlyJson)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      //      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
    }

    "indicate a problem when creating a job without a secret name" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSecretDefsOnlyJson)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      //      contentAsJson(response) \ "message" mustBe JsDefined(JsString("Object is not valid"))
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
      contentAsJson(response) mustBe Json.toJson(UnknownJob(JobId("notexistent")))
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

  "POST /jobs/{id}" should {
    "end as 404 invalid request with error message" in {
      Given("An invalid request")
      val response = route(app, FakeRequest(POST, s"/v1/jobs/${jobSpec1.id}").withJsonBody(jobSpec1Json)).get

      Then("404 response with a message is returned")
      status(response) mustBe NOT_FOUND
      contentAsJson(response) \ "message" mustBe JsDefined(JsString(ErrorHandler.noRouteHandlerMessage))
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

    "update a specific existing job with a different id" in {
      Given("A job spec")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue

      When("The job gets updated")
      val expectedJson = Json.toJson(jobSpec1.copy(labels = Map("a" -> "b")))
      val sendJson = Json.toJson(jobSpec1.copy(id = JobId("ignore.me"), labels = Map("a" -> "b")))
      val response = route(app, FakeRequest(PUT, s"/v1/jobs/${jobSpec1.id}").withJsonBody(sendJson)).get

      Then("The job is updated")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe expectedJson
    }

    "give a 404 for a non existing job" in {
      Given("No job")

      When("A non existing job is updated")
      val response = route(app, FakeRequest(PUT, s"/v1/jobs/notexistent").withJsonBody(jobSpec1Json)).get

      Then("A 404 is sent")
      status(response) mustBe NOT_FOUND
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe Json.toJson(UnknownJob(JobId("notexistent")))
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
      contentAsJson(response) mustBe Json.toJson(UnknownJob(JobId("notexistent")))
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

    "fail for invalid jobspec" in {
      Given("An invalid job spec")
      val jobSpecJson = Json.toJson(JobSpec(JobId("spec1"), run = JobRunSpec()))
      val failed = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecJson)).get

      Then("an error HTTP code is returned")
      status(failed) mustBe UNPROCESSABLE_ENTITY
      contentAsString(failed) must include(JobRunSpecMessages.cmdOrDockerValidation)
    }
  }

  import scala.concurrent.duration._

  def spec(id: String) = JobSpec(JobId(id), run = JobRunSpec(taskKillGracePeriodSeconds = Some(10 seconds), docker = Some(DockerSpec("image", forcePullImage = true))))
  val CronSpec(cron) = "* * * * *"
  val schedule1 = ScheduleSpec("id1", cron)
  val jobSpec1 = spec("spec1")
  val jobSpec1Json = Json.toJson(jobSpec1)
  val jobSpec2 = spec("spec2")
  val jobSpec2Json = Json.toJson(jobSpec2)
  val jobSpecWithSecrets = {
    val jobSpec = spec("spec-with-secrets")
    jobSpec.copy(run = jobSpec.run.copy(env = Map("secretVar" -> EnvVarSecret("secretId")), secrets = Map("secretId" -> SecretDef("source"))))
  }
  val jobSpecWithSecretVarsOnly = {
    val jobSpec = spec("spec-with-secret-vars-only")
    jobSpec.copy(run = jobSpec.run.copy(env = Map("secretVar" -> EnvVarSecret("secretId"))))
  }
  val jobSpecWithSecretDefsOnly = {
    val jobSpec = spec("spec-with-secret-defs-only")
    jobSpec.copy(run = jobSpec.run.copy(secrets = Map("secretId" -> SecretDef("source"))))
  }
  val jobSpecWithSecretsJson = Json.toJson(jobSpecWithSecrets)
  val jobSpecWithSecretVarsOnlyJson = Json.toJson(jobSpecWithSecretVarsOnly)
  val jobSpecWithSecretDefsOnlyJson = Json.toJson(jobSpecWithSecretDefsOnly)
  val auth = new TestAuthFixture

  before {
    auth.authorized = true
    auth.authenticated = true
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context) {
    override lazy val pluginManager: PluginManager = auth.pluginManager
  }
}

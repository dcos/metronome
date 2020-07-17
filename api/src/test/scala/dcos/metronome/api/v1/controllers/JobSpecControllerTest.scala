package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models._
import dcos.metronome.api._
import dcos.metronome.model._
import mesosphere.marathon.core.plugin.PluginManager
import org.scalatest.{BeforeAndAfter, GivenWhenThen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class JobSpecControllerTest
    extends PlaySpec
    with OneAppPerTestWithComponents[MockApiComponents]
    with GivenWhenThen
    with ScalaFutures
    with BeforeAndAfter {

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

    "creates a job when sending a valid job ucr spec" in {
      Given("No job")

      When("A job with UCR is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(ucrJobSpec1Json)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe ucrJobSpec1Json
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

    "creates job when sending ucr spec without forcePullImage property" in {
      Given("Job spec without forcePullImage")
      val specJson = """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"ucr":{"image":{ "id":"image" }}}}"""

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(Json.parse(specJson))).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
    }

    "creates job when sending docker spec with forcePullImage property against v0" in {
      Given("Job spec with forcePullImage")
      val specJson =
        """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"docker":{"image":"image","forcePullImage":true}}}"""

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(Json.parse(specJson))).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
    }

    "creates job when sending ucr spec with forcePullImage property against v0" in {
      Given("Job spec with forcePullImage")
      val specJson =
        """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"ucr":{"image":{"id":"image", "forcePull": true}}}}"""

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

    "creates job when sending ucr spec without forcePullImage property against v0" in {
      Given("Job spec without forcePullImage")
      val specJson = """{"id":"spec1","run":{"cpus":1,"mem":128,"disk":0,"ucr":{"image":{"id":"image"}}}}"""

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

    "creates a job when sending a valid job ucr spec with gpus defined" in {
      Given("No job")

      When("A job with UCR with gpus is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(ucrGpuJobJson)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe ucrGpuJobJson
    }

    "indicates a problem when sending a valid job docker spec with gpus defined" in {
      Given("No job")

      When("A job with Docker gpus is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(dockerGpuJobJson)).get

      contentAsJson(response)

      Then("The job is created")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsString(response).contains("GPUs are not supported with Docker") mustBe true
    }

    "creates a job sending a valid job with cmd and no docker or ucr container" in {
      Given("No job")

      When("A job with CMD with gpus is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(cmdGpuJobJson)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe cmdGpuJobJson
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

    "creates a job when sending a valid job ucr spec with file based secrets defined" in {
      Given("No job")

      When("A job with UCR with file based secrets is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(ucrJobSpecWithFileBasedSecretsJson)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe ucrJobSpecWithFileBasedSecretsJson
    }

    "indicates a problem when sending a valid job docker spec with file based secrets defined" in {
      Given("No job")

      When("A job with Docker with file based secrets is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(dockerJobSpecWithFileBasedSecretsJson)).get

      contentAsJson(response)

      Then("The job is created")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsString(response).contains("File based secrets are only supported by UCR") mustBe true
    }

    "indicate a problem when creating a job without a secret definition" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSecretVarsOnlyJson)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
    }

    "indicate a problem when creating a job only with volume secret but without secret definition" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSecretVolumesOnlyJson)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
    }

    "indicate a problem when creating a job with unused secrets" in {
      Given("No job")

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecWithSecretDefsOnlyJson)).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsString(response).contains("secretId") mustBe true
    }

    "indicate a problem when creating a job which contains fields both for secret and normal volume" in {
      Given("Job spec with wrong volume definition")
      val specJson =
        """{
            |"id":"spec1",
            |"run":{
            |   "cpus":1,
            |   "mem":128,
            |   "disk":0,
            |   "volumes": [
            |     {
            |       "containerPath": "/mnt/test",
            |       "hostPath": "/var/foo",
            |       "secret": "secretId"
            |     }
            |   ],
            |   "ucr":{"image":{"id":"ubuntu"}}
            |}
            |}""".stripMargin

      When("A job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(Json.parse(specJson))).get

      Then("A validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
      contentAsString(response).contains("expected HostVolume or SecretVolume") mustBe true
    }

    "convert EQ and allow IS placement operators in v1" in {
      Given("Job spec with both EQ and IS placement operators")
      val specJson =
        """{
          |  "id": "prod.example.app",
          |  "run": {
          |    "cmd": "sleep 10000",
          |    "cpus": 1,
          |    "mem": 32,
          |    "disk": 128,
          |    "placement": {
          |      "constraints": [
          |        {
          |          "attribute": "heaven",
          |          "operator": "EQ",
          |          "value": "a myth"
          |        },
          |        {
          |          "attribute": "hell",
          |          "operator": "IS",
          |          "value": "round the corner"
          |        }
          |      ]
          |    }
          |  }
          |}""".stripMargin

      When("the job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(Json.parse(specJson))).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")

      Then("the EQ has been converted to an IS operator")
      val constraints = contentAsJson(response).as[JobSpec].run.placement.constraints
      constraints must have size 2
      constraints.contains(ConstraintSpec("heaven", Operator.Is, Some("a myth"))) mustBe true
      constraints.contains(ConstraintSpec("hell", Operator.Is, Some("round the corner"))) mustBe true
    }

    "convert EQ and allow IS placement operators in v0" in {
      Given("Job spec with both EQ and IS placement operators")
      val specJson =
        """{
          |  "id": "prod.example.app",
          |  "run": {
          |    "cmd": "sleep 10000",
          |    "cpus": 1,
          |    "mem": 32,
          |    "disk": 128,
          |    "placement": {
          |      "constraints": [
          |        {
          |          "attribute": "heaven",
          |          "operator": "EQ",
          |          "value": "a myth"
          |        },
          |        {
          |          "attribute": "hell",
          |          "operator": "IS",
          |          "value": "round the corner"
          |        }
          |      ]
          |    }
          |  }
          |}""".stripMargin

      When("the job is created")
      val response = route(app, FakeRequest(POST, s"/v0/scheduled-jobs").withJsonBody(Json.parse(specJson))).get
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")

      Then("the EQ has been converted to an IS operator")
      val constraints = contentAsJson(response).as[JobSpec].run.placement.constraints
      constraints must have size 2
      constraints.contains(ConstraintSpec("heaven", Operator.Is, Some("a myth"))) mustBe true
      constraints.contains(ConstraintSpec("hell", Operator.Is, Some("round the corner"))) mustBe true
    }

    "fail for invalid placement operator" in {
      Given("Job spec with an invalid placement operator")
      val specJson =
        """{
          |  "id": "prod.example.app",
          |  "run": {
          |    "cmd": "sleep 10000",
          |    "cpus": 1,
          |    "mem": 32,
          |    "disk": 128,
          |    "placement": {
          |      "constraints": [
          |        {
          |          "attribute": "foo",
          |          "operator": "invalid",
          |          "value": "bar"
          |        }
          |      ]
          |    }
          |  }
          |}""".stripMargin

      When("the job is created")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(Json.parse(specJson))).get

      Then("a validation error is returned")
      status(response) mustBe UNPROCESSABLE_ENTITY
      contentType(response) mustBe Some("application/json")
    }

    "creates a job with dependencies" in {
      Given("Job A")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobAJson)).get.futureValue

      When("A job B with dependency on job A is posted")
      val response = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobBWithDependencyJson)).get

      Then("The job is created")
      status(response) mustBe CREATED
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe jobBWithDependency
    }
  }

  "GET /jobs" should {
    "get all available jobs" in {
      Given("Three job specs")
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec1Json)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpec2Json)).get.futureValue
      route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(ucrJobSpec1Json)).get.futureValue

      When("All jobspecs are queried")
      val response = route(app, FakeRequest(GET, s"/v1/jobs")).get

      Then("All job specs are returned")
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response).as[JsArray].value.toSet mustBe Set(jobSpec1Json, jobSpec2Json, ucrJobSpec1Json)
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

    "fail for invalid job spec" in {
      Given("An invalid job spec")
      val jobSpecJson = Json.toJson(JobSpec(JobId("spec1"), run = JobRunSpec()))
      val failed = route(app, FakeRequest(POST, s"/v1/jobs").withJsonBody(jobSpecJson)).get

      Then("an error HTTP code is returned")
      status(failed) mustBe UNPROCESSABLE_ENTITY
      contentAsString(failed) must include(JobRunSpecMessages.cmdOrDockerValidation)
    }
  }

  import scala.concurrent.duration._

  def spec(id: String) =
    JobSpec(
      JobId(id),
      run = JobRunSpec(
        taskKillGracePeriodSeconds = Some(10 seconds),
        docker = Some(DockerSpec("image", forcePullImage = true))
      )
    )
  def ucrSpec(id: String) =
    JobSpec(
      JobId(id),
      run = JobRunSpec(
        taskKillGracePeriodSeconds = Some(10 seconds),
        ucr = Some(UcrSpec(ImageSpec(id = "image", forcePull = true)))
      )
    )
  def cmdSpec(id: String) =
    JobSpec(JobId(id), run = JobRunSpec(taskKillGracePeriodSeconds = Some(10 seconds), cmd = Some("sleep")))
  val CronSpec(cron) = "* * * * *"
  val schedule1 = ScheduleSpec("id1", cron)
  val jobSpec1 = spec("spec1")
  val ucrJobSpec1 = ucrSpec("ucr-spec1")
  val jobSpec1Json = Json.toJson(jobSpec1)
  val ucrJobSpec1Json = Json.toJson(ucrJobSpec1)
  val jobSpec2 = spec("spec2")
  val jobSpec2Json = Json.toJson(jobSpec2)
  val jobSpecWithSecrets = {
    val jobSpec = spec("spec-with-secrets")
    jobSpec.copy(run =
      jobSpec.run
        .copy(env = Map("secretVar" -> EnvVarSecret("secretId")), secrets = Map("secretId" -> SecretDef("source")))
    )
  }
  val ucrJobSpecWithFileBasedSecrets = {
    val spec = ucrSpec(id = "ucr-fbs-spec1")
    spec.copy(run =
      spec.run.copy(
        secrets = Map("secretName" -> SecretDef("secretSource")),
        volumes = Seq(SecretVolume("/var/secrets", "secretName"))
      )
    )
  }
  val ucrJobSpecWithFileBasedSecretsJson = Json.toJson(ucrJobSpecWithFileBasedSecrets)
  val dockerJobSpecWithFileBasedSecrets = {
    val jobSpec = spec(id = "ucr-fbs-spec1")
    jobSpec.copy(run =
      jobSpec.run.copy(
        secrets = Map("secretName" -> SecretDef("secretSource")),
        volumes = Seq(SecretVolume("/var/secrets", "secretName"))
      )
    )
  }
  val dockerJobSpecWithFileBasedSecretsJson = Json.toJson(dockerJobSpecWithFileBasedSecrets)
  val jobSpecWithSecretVarsOnly = {
    val jobSpec = spec("spec-with-secret-vars-only")
    jobSpec.copy(run = jobSpec.run.copy(env = Map("secretVar" -> EnvVarSecret("secretId"))))
  }
  val jobSpecWithSecretVolumesOnly = {
    val jobSpec = ucrSpec("spec-with-secret-volumes-only")
    jobSpec.copy(run = jobSpec.run.copy(volumes = Seq(SecretVolume("/var/secrets", "secretName"))))
  }
  val jobSpecWithSecretDefsOnly = {
    val jobSpec = spec("spec-with-secret-defs-only")
    jobSpec.copy(run = jobSpec.run.copy(secrets = Map("secretId" -> SecretDef("source"))))
  }
  val jobSpecWithSecretsJson = Json.toJson(jobSpecWithSecrets)
  val jobSpecWithSecretVarsOnlyJson = Json.toJson(jobSpecWithSecretVarsOnly)
  val jobSpecWithSecretVolumesOnlyJson = Json.toJson(jobSpecWithSecretVolumesOnly)
  val jobSpecWithSecretDefsOnlyJson = Json.toJson(jobSpecWithSecretDefsOnly)

  val ucrGpuJob = {
    val s = ucrSpec("ucr-gpu")
    s.copy(run = s.run.copy(gpus = 4))
  }
  val ucrGpuJobJson = Json.toJson(ucrGpuJob)
  val dockerGpuJob = {
    val s = spec(id = "ucr-gpu")
    s.copy(run = s.run.copy(gpus = 4))
  }
  val dockerGpuJobJson = Json.toJson(dockerGpuJob)
  val auth = new TestAuthFixture

  val cmdGpuJob = {
    val s = cmdSpec("cmd-gpu")
    s.copy(run = s.run.copy(gpus = 4))
  }
  val cmdGpuJobJson = Json.toJson(cmdGpuJob)

  val jobA = spec("A")
  val jobAJson = Json.toJson(jobA)
  val jobBWithDependency = spec("B").copy(dependencies = Seq(jobA.id))
  val jobBWithDependencyJson = Json.toJson(jobBWithDependency)

  before {
    auth.authorized = true
    auth.authenticated = true
  }

  override def createComponents(context: Context): MockApiComponents =
    new MockApiComponents(context) {
      override lazy val pluginManager: PluginManager = auth.pluginManager
    }
}

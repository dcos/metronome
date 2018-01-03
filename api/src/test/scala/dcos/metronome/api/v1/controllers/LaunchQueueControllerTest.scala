package dcos.metronome.api.v1.controllers

import dcos.metronome.api.{ MockApiComponents, OneAppPerTestWithComponents }
import dcos.metronome.model.{ JobId, JobRunSpec, JobRunStatus, QueuedJobRunInfo }
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.state.Timestamp
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{ OneAppPerTest, PlaySpec }
import play.api.ApplicationLoader.Context
import play.api.test.FakeRequest
import play.api.test.Helpers.{ GET, route }
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, Json }
import play.api.test.Helpers._
import dcos.metronome.api.v1.models.JsonConverters._

class LaunchQueueControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] with ScalaFutures with MockitoSugar {

  private val queueServiceMock = mock[LaunchQueueService]

  "GET /queue" should {
    "return list of jobs in the queue" in {
      val queuedJobRun = QueuedJobRunInfo(JobId("job"), 1, Timestamp.zero, JobRunSpec())
      val queuedJobList = List(queuedJobRun)
      when(queueServiceMock.list()).thenReturn(queuedJobList)
      val response = route(app, FakeRequest(GET, "/v1/queue")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe queueToJson(queuedJobList.groupBy(_.jobid))
    }
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context) {
    override lazy val queueService: LaunchQueueService = queueServiceMock
  }
}

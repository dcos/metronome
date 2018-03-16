package dcos.metronome
package api.v1.controllers

import dcos.metronome.api.v1.models.QueuedJobRunMapWrites
import dcos.metronome.api.{ MockApiComponents, OneAppPerTestWithComponents, TestAuthFixture }
import dcos.metronome.model.{ JobId, JobRunSpec, QueuedJobRunInfo }
import dcos.metronome.queue.LaunchQueueService
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.state.Timestamp
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.ApplicationLoader.Context
import play.api.test.FakeRequest
import play.api.test.Helpers.{ GET, route, _ }

class LaunchQueueControllerTest extends PlaySpec with OneAppPerTestWithComponents[MockApiComponents] with ScalaFutures with MockitoSugar with BeforeAndAfter {

  private val queueServiceMock = mock[LaunchQueueService]

  "GET /queue" should {
    "return list of jobs in the queue" in {
      val queuedJobRun = QueuedJobRunInfo(JobId("job"), Timestamp.zero, JobRunSpec())
      val queuedJobList = List(queuedJobRun)
      when(queueServiceMock.list()).thenReturn(queuedJobList)
      val response = route(app, FakeRequest(GET, "/v1/queue")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsJson(response) mustBe QueuedJobRunMapWrites.writes(queuedJobList.groupBy(_.jobId))
    }

    "return nothing when not authorized to see the job" in {
      auth.authorized = false
      val queuedJobList = List(QueuedJobRunInfo(JobId("job"), Timestamp.zero, JobRunSpec()))
      when(queueServiceMock.list()).thenReturn(queuedJobList)
      val response = route(app, FakeRequest(GET, "/v1/queue")).get

      contentAsJson(response) mustBe QueuedJobRunMapWrites.writes(Map.empty)
    }
  }

  val auth = new TestAuthFixture

  before {
    auth.authorized = true
    auth.authenticated = true
  }

  override def createComponents(context: Context): MockApiComponents = new MockApiComponents(context) {
    override lazy val queueService: LaunchQueueService = queueServiceMock
    override lazy val pluginManager: PluginManager = auth.pluginManager
  }
}

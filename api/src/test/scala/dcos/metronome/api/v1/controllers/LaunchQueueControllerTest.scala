package dcos.metronome.api.v1.controllers

import dcos.metronome.queue.LaunchQueueService
import org.mockito.Mockito
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route}

class LaunchQueueControllerTest extends PlaySpec with OneAppPerTest with MockitoSugar {
  "GET /queue" should {
    "return list of jobs in the queue" in {
      val f = new Fixture
      val response = f.controller.queue().apply(FakeRequest()).get
    }
  }

  case class Fixture(launchQueueService: LaunchQueueService = mock[LaunchQueueService]) {
    val controller = new LaunchQueueController(launchQueueService)
  }
}

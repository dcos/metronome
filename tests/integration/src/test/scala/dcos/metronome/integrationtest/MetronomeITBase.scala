package dcos.metronome.integrationtest

import java.util.UUID

import com.mesosphere.utils.AkkaUnitTest
import com.mesosphere.utils.http.RestResult
import com.mesosphere.utils.mesos.MesosClusterTest
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.integrationtest.utils.{ MetronomeFacade, MetronomeFramework }
import org.apache.mesos.v1.Protos.FrameworkID
import org.scalatest.Inside
import org.scalatest.matchers.{ BeMatcher, MatchResult }

import scala.concurrent.duration._

class MetronomeITBase extends AkkaUnitTest
    with MesosClusterTest
    with Inside
    with RestResultMatchers
    with StrictLogging {

  override lazy implicit val patienceConfig = PatienceConfig(180.seconds, interval = 1.second)

  def withFixture(frameworkId: Option[FrameworkID.Builder] = None)(fn: Fixture => Unit): Unit = {
    val f = new Fixture(frameworkId)
    try fn(f)
    finally {
      f.metronomeFramework.stop()
    }
  }

  class Fixture(existingFrameworkId: Option[FrameworkID.Builder] = None) extends StrictLogging {
    logger.info("Create Fixture with new Metronome...")

    val zkUrl = s"zk://${zkserver.connectUrl}/metronome_${UUID.randomUUID()}"
    val masterUrl = mesosFacade.url.getHost + ":" + mesosFacade.url.getPort

    val currentITName = MetronomeITBase.this.getClass.getSimpleName

    val metronomeFramework = MetronomeFramework.LocalMetronome(currentITName, masterUrl, zkUrl)

    logger.info("Starting metronome...")
    metronomeFramework.start().futureValue

    logger.info(s"Metronome started, reachable on: ${metronomeFramework.url}")
    lazy val metronome: MetronomeFacade = metronomeFramework.facade
  }

}

// TODO: The matchers should be pulled from USI.

/**
  * Custom matcher for HTTP responses that print response body.
  * @param status The expected status code.
  */
class RestResultMatcher(status: Int) extends BeMatcher[RestResult[_]] {
  def apply(left: RestResult[_]) =
    MatchResult(
      left.code == status,
      s"Response code was not $status but ${left.code} with body '${left.entityString}'",
      s"Response code was $status with body '${left.entityString}'")
}

trait RestResultMatchers {
  val OK = new RestResultMatcher(200)
  val Created = new RestResultMatcher(201)
  val Accepted = new RestResultMatcher(202)
  val NoContent = new RestResultMatcher(204)
  val Redirect = new RestResultMatcher(302)
  val NotFound = new RestResultMatcher(404)
  val Conflict = new RestResultMatcher(409)
  val UnprocessableEntity = new RestResultMatcher(422)
  val ServerError = new RestResultMatcher(500)
  val BadGateway = new RestResultMatcher(502)
}

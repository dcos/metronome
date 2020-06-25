package dcos.metronome.integrationtest

import java.util.UUID

import com.mesosphere.utils.AkkaUnitTest
import com.mesosphere.utils.http.RestResultMatchers
import com.mesosphere.utils.mesos.MesosClusterTest
import com.typesafe.scalalogging.StrictLogging
import dcos.metronome.integrationtest.utils.{MetronomeFacade, MetronomeFramework}
import org.apache.mesos.v1.Protos.FrameworkID
import org.scalatest.Inside

import scala.concurrent.duration._

class MetronomeITBase
    extends AkkaUnitTest
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

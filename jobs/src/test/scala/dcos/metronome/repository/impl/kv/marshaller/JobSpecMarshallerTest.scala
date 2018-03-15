package dcos.metronome
package repository.impl.kv.marshaller

import java.time.ZoneId

import dcos.metronome.model._
import org.scalatest.{ FunSuite, Matchers }

import scala.collection.immutable._

class JobSpecMarshallerTest extends FunSuite with Matchers {
  test("round-trip of a complete JobSpec with cmd") {
    val f = new Fixture
    JobSpecMarshaller.fromBytes(JobSpecMarshaller.toBytes(f.jobSpec)) should be (Some(f.jobSpec))
  }

  test("round-trip of a complete JobSpec with args") {
    val f = new Fixture

    val jobSpec = f.jobSpec.copy(
      run = f.runSpec.copy(cmd = None, args = Some(Seq("first", "second"))))
    JobSpecMarshaller.fromBytes(JobSpecMarshaller.toBytes(f.jobSpec)) should be (Some(f.jobSpec))
  }

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobSpecMarshaller.fromBytes(invalidBytes) should be (None)
  }

  class Fixture {
    import concurrent.duration._

    val runSpec = JobRunSpec(
      cpus = 42.0,
      mem = 133.7,
      disk = 3133.7,
      cmd = Some("sleep 500"),
      args = None,
      user = Some("root"),
      env = Map("key" -> "value"),
      placement = PlacementSpec(constraints = Seq(ConstraintSpec("hostname", Operator.Eq, Some("localhost")))),
      artifacts = Seq(Artifact("http://www.foo.bar/file.tar.gz", extract = false, executable = true, cache = true)),
      maxLaunchDelay = 24.hours,
      docker = Some(DockerSpec(image = "dcos/metronome", true)),
      volumes = Seq(
        Volume(containerPath = "/var/log", hostPath = "/sandbox/task1/var/log", mode = Mode.RW)),
      restart = RestartSpec(policy = RestartPolicy.OnFailure, activeDeadline = Some(15.days)),
      taskKillGracePeriodSeconds = Some(10 seconds))

    val jobSpec = JobSpec(
      id = JobId("/foo/bar"),
      description = Some("My description"),
      labels = Map("stage" -> "production"),
      schedules = Seq(ScheduleSpec(
        id = "my-schedule",
        cron = CronSpec("* * * * *"),
        timeZone = ZoneId.of("UTC"),
        concurrencyPolicy = ConcurrencyPolicy.Allow,
        enabled = true)),
      run = runSpec)
  }
}

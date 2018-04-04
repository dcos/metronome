package dcos.metronome
package repository.impl.kv.marshaller

import java.time.ZoneId

import dcos.metronome.model._
import org.scalacheck.{ Arbitrary, Gen }
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks

import scala.collection.immutable._
import scala.concurrent.duration.FiniteDuration

class JobSpecMarshallerTest extends FunSuite with Matchers with PropertyChecks {

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobSpecMarshaller.fromBytes(invalidBytes.to[IndexedSeq]) should be (None)
  }

  val nonEmptyAlphaStrGen = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  val constrantsSpecGen = for {
    attribute <- nonEmptyAlphaStrGen
    operator <- Gen.oneOf(Operator.Eq, Operator.Like, Operator.Unlike)
    value <- Gen.option(nonEmptyAlphaStrGen)
  } yield ConstraintSpec(
    attribute,
    operator,
    value)

  val placementGen = for {
    constraints <- Gen.listOf(constrantsSpecGen)
  } yield PlacementSpec(
    constraints)

  val booleanGen = Gen.oneOf(true, false)

  val durationGen = Gen.posNum[Long].map(l => FiniteDuration(l, "second"))

  val jobRunSpecGen = for {
    cpus <- Gen.posNum[Double]
    mem <- Gen.posNum[Double]
    disk <- Gen.posNum[Double]
    cmd <- Gen.option(nonEmptyAlphaStrGen)
    args <- Gen.option(Gen.nonEmptyListOf(nonEmptyAlphaStrGen))
    user <- Gen.option(nonEmptyAlphaStrGen)
    env <- Gen.mapOf(Gen.zip(nonEmptyAlphaStrGen, Gen.oneOf(nonEmptyAlphaStrGen.map(v => EnvVarValue(v)), nonEmptyAlphaStrGen.map(v => EnvVarSecret(v)))))
    placement <- placementGen
    artifacts <- Gen.listOf(Gen.zip(nonEmptyAlphaStrGen, booleanGen, booleanGen, booleanGen).map { case (s, b1, b2, b3) => Artifact(s, b1, b2, b3) })
    maxLaunchDelay <- durationGen
    docker <- Gen.option(Gen.zip(nonEmptyAlphaStrGen, booleanGen).map{ case (s, b) => DockerSpec(s, b) })
    volumes <- Gen.listOf(Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen, Gen.oneOf(Mode.RO, Mode.RW)).map{ case (s1, s2, m) => Volume(s1, s2, m) })
    restart <- Gen.zip(Gen.oneOf(RestartPolicy.Never, RestartPolicy.OnFailure), Gen.option(durationGen)).map{ case (rp, d) => RestartSpec(rp, d) }
    taskKillGracePeriodSeconds <- Gen.option(durationGen)
    secrets <- Gen.mapOf(Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen.map(s => SecretDef(s))))
  } yield JobRunSpec(
    cpus,
    mem,
    disk,
    cmd,
    args,
    user,
    env,
    placement,
    artifacts,
    maxLaunchDelay,
    docker,
    volumes,
    restart,
    taskKillGracePeriodSeconds,
    secrets)

  val jobSpecGen = for {
    id <- Gen.listOf(nonEmptyAlphaStrGen).map(s => JobId(s))
    description <- Gen.option(nonEmptyAlphaStrGen)
    labels <- Gen.mapOf(Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen))
    schedules <- Gen.listOf(for {
      id <- nonEmptyAlphaStrGen
      cron <- Gen.const(CronSpec("* * * * *"))
      timeZone <- Gen.const(ZoneId.of("UTC"))
      startingDeadline <- durationGen
      concurrencyPolicy <- Gen.const(ConcurrencyPolicy.Allow)
      enabled <- booleanGen
    } yield ScheduleSpec(id, cron, timeZone, startingDeadline, concurrencyPolicy, enabled))
    run <- jobRunSpecGen
  } yield JobSpec(id, description, labels, schedules, run)

  implicit val jobSpecArbitrary = Arbitrary(jobSpecGen)

  test("round-trip of a complete JobSpec") {
    forAll (jobSpecGen) { jobSpec =>
      JobSpecMarshaller.fromBytes(JobSpecMarshaller.toBytes(jobSpec)) shouldEqual Some(jobSpec)
    }
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
      env = Map("key" -> EnvVarValue("value"), "secretName" -> EnvVarSecret("secretId")),
      placement = PlacementSpec(constraints = Seq(ConstraintSpec("hostname", Operator.Eq, Some("localhost")))),
      artifacts = Seq(Artifact("http://www.foo.bar/file.tar.gz", extract = false, executable = true, cache = true)),
      maxLaunchDelay = 24.hours,
      docker = Some(DockerSpec(image = "dcos/metronome", true)),
      volumes = Seq(
        Volume(containerPath = "/var/log", hostPath = "/sandbox/task1/var/log", mode = Mode.RW)),
      restart = RestartSpec(policy = RestartPolicy.OnFailure, activeDeadline = Some(15.days)),
      taskKillGracePeriodSeconds = Some(10 seconds),
      secrets = Map("secretId" -> SecretDef("source")))

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

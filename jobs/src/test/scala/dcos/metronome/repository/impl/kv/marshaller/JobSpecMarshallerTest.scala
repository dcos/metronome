package dcos.metronome
package repository.impl.kv.marshaller

import java.time.ZoneId

import dcos.metronome.model._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{FunSuite, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.collection.immutable._
import scala.concurrent.duration.FiniteDuration

class JobSpecMarshallerTest extends FunSuite with Matchers with ScalaCheckPropertyChecks {

  test("unmarshal with invalid proto data should return None") {
    val invalidBytes = "foobar".getBytes
    JobSpecMarshaller.fromBytes(invalidBytes.to[IndexedSeq]) should be(None)
  }

  val nonEmptyAlphaStrGen = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  val constrantsSpecGen = for {
    attribute <- nonEmptyAlphaStrGen
    operator <- Gen.oneOf(Operator.Is, Operator.Like, Operator.Unlike)
    value <- Gen.option(nonEmptyAlphaStrGen)
  } yield ConstraintSpec(attribute, operator, value)

  val placementGen = for {
    constraints <- Gen.listOf(constrantsSpecGen)
  } yield PlacementSpec(constraints)

  val booleanGen = Gen.oneOf(true, false)

  val durationGen = Gen.posNum[Long].map(l => FiniteDuration(l, "second"))

  val dockerGen = Gen.option(Gen.zip(nonEmptyAlphaStrGen, booleanGen).map { case (s, b) => DockerSpec(s, b) })

  val ucrGen = Gen.option(
    Gen.zip(nonEmptyAlphaStrGen, booleanGen).map { case (id, fp) => UcrSpec(ImageSpec(id = id, forcePull = fp)) }
  )

  val dockerOrUcrGen: Gen[Option[Container]] = Gen.oneOf(dockerGen, ucrGen)

  val hostVolumeGen = Gen.listOf(Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen, Gen.oneOf(Mode.RO, Mode.RW)).map {
    case (s1, s2, m) => HostVolume(s1, s2, m)
  })

  val secretVolumeGen =
    Gen.listOf(Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen).map { case (s1, s2) => SecretVolume(s1, s2) })

  val volumeGen = Gen.oneOf(hostVolumeGen, secretVolumeGen)

  val userNetworkGen: Gen[Network] = {
    Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen, nonEmptyAlphaStrGen).map {
      case (name, labelKey, labelValue) =>
        Network(name = Some(name), mode = Network.NetworkMode.Container, labels = Map(labelKey -> labelValue))
    }
  }

  val hostNetworkGen = Gen.const(Network(name = None, mode = Network.NetworkMode.Host, labels = Map.empty))
  val networkGen: Gen[Network] = Gen.oneOf[Network](userNetworkGen, hostNetworkGen)

  val jobRunSpecGen = for {
    cpus <- Gen.posNum[Double]
    mem <- Gen.posNum[Double]
    disk <- Gen.posNum[Double]
    gpus <- Gen.posNum[Int]
    cmd <- Gen.option(nonEmptyAlphaStrGen)
    args <- Gen.option(Gen.nonEmptyListOf(nonEmptyAlphaStrGen))
    user <- Gen.option(nonEmptyAlphaStrGen)
    env <- Gen.mapOf(
      Gen.zip(
        nonEmptyAlphaStrGen,
        Gen.oneOf(nonEmptyAlphaStrGen.map(v => EnvVarValue(v)), nonEmptyAlphaStrGen.map(v => EnvVarSecret(v)))
      )
    )
    placement <- placementGen
    artifacts <- Gen.listOf(Gen.zip(nonEmptyAlphaStrGen, booleanGen, booleanGen, booleanGen).map {
      case (s, b1, b2, b3) => Artifact(s, b1, b2, b3)
    })
    maxLaunchDelay <- durationGen
    container <- dockerOrUcrGen
    volumes <- volumeGen
    networks <- networkGen
    restart <- Gen.zip(Gen.oneOf(RestartPolicy.Never, RestartPolicy.OnFailure), Gen.option(durationGen)).map {
      case (rp, d) => RestartSpec(rp, d)
    }
    taskKillGracePeriodSeconds <- Gen.option(durationGen)
    secrets <- Gen.mapOf(Gen.zip(nonEmptyAlphaStrGen, nonEmptyAlphaStrGen.map(s => SecretDef(s))))
  } yield {

    val docker = container.collect { case c: DockerSpec => c }
    val ucr = container.collect { case c: UcrSpec => c }

    JobRunSpec(
      cpus,
      mem,
      disk,
      gpus,
      cmd,
      args,
      user,
      env,
      placement,
      artifacts,
      maxLaunchDelay,
      docker,
      ucr,
      volumes,
      restart,
      taskKillGracePeriodSeconds,
      secrets,
      Seq(networks)
    )
  }

  val jobIdGen = Gen.listOf(nonEmptyAlphaStrGen).map(s => JobId(s))

  val jobSpecGen = for {
    id <- jobIdGen
    description <- Gen.option(nonEmptyAlphaStrGen)
    //dependencies <- Gen.listOf(jobIdGen)
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
  } yield JobSpec(id, description, Seq.empty, labels, schedules, run) // TODO: serialize dependencies

  implicit val jobSpecArbitrary = Arbitrary(jobSpecGen)

  test("round-trip of a complete JobSpec") {
    1.until(20).foreach { _ =>
      forAll(jobSpecGen) { jobSpec =>
        JobSpecMarshaller.fromBytes(JobSpecMarshaller.toBytes(jobSpec)) shouldEqual Some(jobSpec)
      }
    }
  }

  test("reading EQ constraint comes through as IS") {
    // This asserts that jobSpecs stored with the EQ Operator are properly mapped to IS
    import Protos.JobSpec.RunSpec.PlacementSpec.Constraint
    import RunSpecConversions.ProtosToConstraintSpec
    val converted = Seq(
      Constraint.newBuilder().setAttribute("field").setOperator(Constraint.Operator.EQ).setValue("value").build
    ).toModel
    converted.head.operator.name shouldBe "IS"
  }
}

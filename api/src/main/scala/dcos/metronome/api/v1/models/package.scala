package dcos.metronome.api.v1

import dcos.metronome.api.{UnknownJob, ErrorDetail}
import dcos.metronome.model._
import mesosphere.marathon.state._
import org.joda.time.DateTimeZone
import play.api.data.validation.ValidationError
import play.api.libs.json.{ OFormat, Reads, Json }
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import org.apache.mesos.{ Protos => mesos }
import play.api.libs.json.Reads._

import scala.concurrent.duration.FiniteDuration

package object models {

  implicit class ReadsWithDefault[A](val reads: Reads[Option[A]]) extends AnyVal {
    def withDefault(a: A): Reads[A] = reads.map(_.getOrElse(a))
  }

  implicit class FormatWithDefault[A](val m: OFormat[Option[A]]) extends AnyVal {
    def withDefault(a: A): OFormat[A] = m.inmap(_.getOrElse(a), Some(_))
  }

  implicit class ReadsAsSeconds(val reads: Reads[Long]) extends AnyVal {
    def asSeconds: Reads[FiniteDuration] = reads.map(_.seconds)
  }

  implicit class FormatAsSeconds(val format: OFormat[Long]) extends AnyVal {
    def asSeconds: OFormat[FiniteDuration] =
      format.inmap(
        _.seconds,
        _.toSeconds
      )
  }

  def enumFormat[A <: java.lang.Enum[A]](read: String => A, errorMsg: String => String): Format[A] = {
    val reads = Reads[A] {
      case JsString(str) =>
        try {
          JsSuccess(read(str))
        }
        catch {
          case _: IllegalArgumentException => JsError(errorMsg(str))
        }
      case x: JsValue => JsError(s"expected string, got $x")
    }

    val writes = Writes[A] { a: A => JsString(a.name) }

    Format(reads, writes)
  }

  implicit val errorFormat: Format[ErrorDetail] = Json.format[ErrorDetail]
  implicit val unknownJobsFormat: Format[UnknownJob] = Json.format[UnknownJob]

  implicit lazy val ArtifactFormat: Format[Artifact] = (
      (__ \ "url").format[String] ~
      (__ \ "extract").formatNullable[Boolean].withDefault(true) ~
      (__ \ "executable").formatNullable[Boolean].withDefault(false) ~
      (__ \ "cache").formatNullable[Boolean].withDefault(false)
    )(Artifact.apply, unlift(Artifact.unapply))

  implicit lazy val CronFormat: Format[CronSpec] = new Format[CronSpec] {
    override def writes(o: CronSpec): JsValue = JsString(o.toString)
    override def reads(json: JsValue): JsResult[CronSpec] = json match {
      case JsString(CronSpec(value)) => JsSuccess(value)
      case invalid               => JsError(s"Can not read cron expression $invalid")
    }
  }

  implicit lazy val DateTimeZoneFormat: Format[DateTimeZone] = new Format[DateTimeZone] {
    override def writes(o: DateTimeZone): JsValue = JsString(o.getID)
    override def reads(json: JsValue): JsResult[DateTimeZone] = json match {
      case JsString(value) if DateTimeZone.getAvailableIDs.asScala.contains(value) => JsSuccess(DateTimeZone.forID(value))
      case invalid => JsError(s"No time zone found with this id: $invalid")
    }
  }

  implicit lazy val DurationFormat: Format[Duration] = new Format[Duration] {
    override def writes(o: Duration): JsValue = JsNumber(o.toSeconds)
    override def reads(json: JsValue): JsResult[Duration] = json match {
      case JsNumber(value) if value >= 0 => JsSuccess(value.toLong.seconds)
      case invalid                       => JsError(s"Can not read duration: $invalid")
    }
  }

  implicit lazy val ConcurrencyPolicyFormat: Format[ConcurrencyPolicy] = new Format[ConcurrencyPolicy] {
    override def writes(o: ConcurrencyPolicy): JsValue = JsString(ConcurrencyPolicy.name(o))
    override def reads(json: JsValue): JsResult[ConcurrencyPolicy] = json match {
      case JsString(ConcurrencyPolicy(value)) => JsSuccess(value)
      case invalid                            => JsError(s"Can not read concurrency policy $invalid")
    }
  }

  implicit lazy val RestartPolicyFormat: Format[RestartPolicy] = new Format[RestartPolicy] {
    override def writes(o: RestartPolicy): JsValue = JsString(RestartPolicy.name(o))
    override def reads(json: JsValue): JsResult[RestartPolicy] = json match {
      case JsString(RestartPolicy(value)) => JsSuccess(value)
      case invalid                            => JsError(s"Can not read restart policy $invalid")
    }
  }

  implicit lazy val ScheduleSpecFormat: Format[ScheduleSpec] = (
      (__ \ "schedule").format[CronSpec] ~
      (__ \ "timezone").formatNullable[DateTimeZone].withDefault(ScheduleSpec.DefaultTimeZone) ~
      (__ \ "startingDeadlineSeconds").formatNullable[Duration].withDefault(ScheduleSpec.DefaultStartingDeadline)  ~
      (__ \ "concurrencyPolicy").formatNullable[ConcurrencyPolicy].withDefault(ScheduleSpec.DefaultConcurrencyPolicy) ~
      (__ \ "enabled").formatNullable[Boolean].withDefault(ScheduleSpec.DefaultEnabled)
    )(ScheduleSpec.apply, unlift(ScheduleSpec.unapply))

  implicit lazy val PortSpecFormat: Format[PortSpec] = (
      (__ \ "protocol").format[String] ~
      (__ \ "name").format[String] ~
      (__ \ "hostPort").formatNullable[Int] ~
      (__ \ "containerPort").formatNullable[Int] ~
      (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty)
    )(PortSpec.apply, unlift(PortSpec.unapply))


  implicit lazy val ConstraintSpecFormat: Format[ConstraintSpec] = (
      (__ \ "attr").format[String] ~
      (__ \ "op").format[String](filter[String](ValidationError("Invalid Operator"))(ConstraintSpec.isValidOperation)) ~
      (__ \ "value").formatNullable[String]
    )(ConstraintSpec.apply, unlift(ConstraintSpec.unapply))

  implicit lazy val PlacementSpecFormat: Format[PlacementSpec] = Json.format[PlacementSpec]

  implicit lazy val ModeFormat: Format[mesos.Volume.Mode] =
    enumFormat(mesos.Volume.Mode.valueOf, str => s"$str is not a valid mde")

  implicit lazy val PersistentVolumeInfoFormat: Format[PersistentVolumeInfo] = Json.format[PersistentVolumeInfo]

  implicit lazy val ExternalVolumeInfoFormat: Format[ExternalVolumeInfo] = (
      (__ \ "size").formatNullable[Long] ~
      (__ \ "name").format[String] ~
      (__ \ "provider").format[String] ~
      (__ \ "options").formatNullable[Map[String, String]].withDefault(Map.empty[String, String])
    )(ExternalVolumeInfo.apply, unlift(ExternalVolumeInfo.unapply))

  implicit lazy val VolumeFormat: Format[Volume] = (
      (__ \ "containerPath").format[String] ~
      (__ \ "hostPath").formatNullable[String] ~
      (__ \ "mode").format[mesos.Volume.Mode] ~
      (__ \ "persistent").formatNullable[PersistentVolumeInfo] ~
      (__ \ "external").formatNullable[ExternalVolumeInfo]
    )(Volume.apply, unlift(Volume.unapply))

  implicit lazy val ParameterFormat: Format[Parameter] = (
      (__ \ "key").format[String] ~
      (__ \ "value").format[String]
    )(Parameter(_, _), unlift(Parameter.unapply))

  implicit lazy val DockerSpecFormat: Format[DockerSpec] = (
      (__ \ "image").format[String] ~
      (__ \ "network").formatNullable[String].withDefault(DockerSpec.DefaultNetwork) ~
      (__ \ "privileged").formatNullable[Boolean].withDefault(DockerSpec.DefaultPrivileged) ~
      (__ \ "parameters").formatNullable[Seq[Parameter]].withDefault(DockerSpec.DefaultParameter) ~
      (__ \ "forcePullImage").formatNullable[Boolean].withDefault(DockerSpec.DefaultForcePullImage)
    ) (DockerSpec.apply, unlift(DockerSpec.unapply))

  implicit lazy val RunSpecFormat: Format[RunSpec] = (
      (__ \ "cpus").formatNullable[Double].withDefault(RunSpec.DefaultCpus) ~
      (__ \ "mem").formatNullable[Double].withDefault(RunSpec.DefaultMem) ~
      (__ \ "disk").formatNullable[Double].withDefault(RunSpec.DefaultDisk) ~
      (__ \ "cmd").formatNullable[String] ~
      (__ \ "args").formatNullable[Seq[String]] ~
      (__ \ "user").formatNullable[String] ~
      (__ \ "env").formatNullable[Map[String, String]].withDefault(RunSpec.DefaultEnv) ~
      (__ \ "placement").formatNullable[PlacementSpec].withDefault(RunSpec.DefaultPlacement) ~
      (__ \ "artifacts").formatNullable[Seq[Artifact]].withDefault(RunSpec.DefaultArtifacts) ~
      (__ \ "maxLaunchDelay").formatNullable[Duration].withDefault(RunSpec.DefaultMaxLaunchDelay) ~
      (__ \ "docker").formatNullable[DockerSpec] ~
      (__ \ "ports").formatNullable[Seq[PortSpec]].withDefault(RunSpec.DefaultPorts) ~
      (__ \ "volumes").formatNullable[Seq[Volume]].withDefault(RunSpec.DefaultVolumes) ~
      (__ \ "restartPolicy").formatNullable[RestartPolicy].withDefault(RunSpec.DefaultRestartPolicy) ~
      (__ \ "activeDeadlineSeconds").formatNullable[Duration].withDefault(RunSpec.DefaultActiveDeadline)
    )(RunSpec.apply, unlift(RunSpec.unapply))


  implicit lazy val PathIdFormat: Format[PathId] = Format(
    Reads.of[String](Reads.minLength[String](1)).map(PathId(_)).filterNot(_.isRoot),
    Writes[PathId] { id => JsString(id.toString) }
  )

  implicit lazy val JobSpecFormat: Format[JobSpec] = (
      (__ \ "id").format[PathId] ~
      (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
      (__ \ "schedule").formatNullable[ScheduleSpec] ~
      (__ \ "run").format[RunSpec]
    )(JobSpec.apply, unlift(JobSpec.unapply))
}

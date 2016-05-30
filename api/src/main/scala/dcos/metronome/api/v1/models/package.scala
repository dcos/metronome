package dcos.metronome.api.v1

import dcos.metronome.api.{ ErrorDetail, UnknownJob }
import dcos.metronome.model._
import mesosphere.marathon.state.{ Volume => _, _ }
import org.apache.mesos.Protos.ContainerInfo.DockerInfo
import org.apache.mesos.{ Protos => mesos }
import org.joda.time.DateTimeZone
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{ Json, _ }

import scala.collection.JavaConverters._
import scala.concurrent.duration._
package object models {

  import mesosphere.marathon.api.v2.json.Formats.{ FormatWithDefault, PathIdFormat, enumFormat }

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
      case invalid                   => JsError(s"Can not read cron expression $invalid")
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
      case invalid                            => JsError(s"'$invalid' is not a valid concurrency policy. Allowed values: ${ConcurrencyPolicy.names}")
    }
  }

  implicit lazy val RestartPolicyFormat: Format[RestartPolicy] = new Format[RestartPolicy] {
    override def writes(o: RestartPolicy): JsValue = JsString(RestartPolicy.name(o))
    override def reads(json: JsValue): JsResult[RestartPolicy] = json match {
      case JsString(RestartPolicy(value)) => JsSuccess(value)
      case invalid                        => JsError(s"'$invalid' is not a valid restart policy. Allowed values: ${RestartPolicy.names}")
    }
  }

  implicit lazy val ScheduleSpecFormat: Format[ScheduleSpec] = (
    (__ \ "cron").format[CronSpec] ~
    (__ \ "timezone").formatNullable[DateTimeZone].withDefault(ScheduleSpec.DefaultTimeZone) ~
    (__ \ "startingDeadlineSeconds").formatNullable[Duration].withDefault(ScheduleSpec.DefaultStartingDeadline) ~
    (__ \ "concurrencyPolicy").formatNullable[ConcurrencyPolicy].withDefault(ScheduleSpec.DefaultConcurrencyPolicy) ~
    (__ \ "enabled").formatNullable[Boolean].withDefault(ScheduleSpec.DefaultEnabled)
  )(ScheduleSpec.apply, unlift(ScheduleSpec.unapply))

  implicit lazy val ConstraintSpecFormat: Format[ConstraintSpec] = (
    (__ \ "attr").format[String] ~
    (__ \ "op").format[String](filter[String](ValidationError(s"Invalid Operator. Allowed values: ${ConstraintSpec.AvailableOperations}"))(ConstraintSpec.isValidOperation)) ~
    (__ \ "value").formatNullable[String]
  )(ConstraintSpec.apply, unlift(ConstraintSpec.unapply))

  implicit lazy val PlacementSpecFormat: Format[PlacementSpec] = Json.format[PlacementSpec]

  implicit lazy val ModeFormat: Format[mesos.Volume.Mode] =
    enumFormat(mesos.Volume.Mode.valueOf, str => s"$str is not a valid mode")

  implicit lazy val VolumeFormat: Format[Volume] = Json.format[Volume]

  implicit lazy val DockerSpecFormat: Format[DockerSpec] = Json.format[DockerSpec]

  implicit lazy val RestartSpecFormat: Format[RestartSpec] = (
    (__ \ "restartPolicy").formatNullable[RestartPolicy].withDefault(RestartSpec.DefaultRestartPolicy) ~
    (__ \ "activeDeadlineSeconds").formatNullable[Duration]
  ) (RestartSpec.apply, unlift(RestartSpec.unapply))

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
    (__ \ "volumes").formatNullable[Seq[Volume]].withDefault(RunSpec.DefaultVolumes) ~
    (__ \ "restart").formatNullable[RestartSpec].withDefault(RunSpec.DefaultRestartSpec)
  )(RunSpec.apply, unlift(RunSpec.unapply))

  implicit lazy val JobSpecFormat: Format[JobSpec] = (
    (__ \ "id").format[PathId] ~
    (__ \ "description").format[String] ~
    (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
    (__ \ "schedule").formatNullable[ScheduleSpec] ~
    (__ \ "run").format[RunSpec]
  )(JobSpec.apply, unlift(JobSpec.unapply))
}

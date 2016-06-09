package dcos.metronome.api.v1

import dcos.metronome.api.{ ErrorDetail, UnknownJob }
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model.Event.{ JobSpecCreated, _ }
import dcos.metronome.model._
import mesosphere.marathon.state.PathId
import org.apache.mesos.{ Protos => mesos }
import org.joda.time.{ DateTime, DateTimeZone }
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

  implicit val DateTimeFormat: Format[DateTime] = Format(
    Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
    Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  )

  implicit lazy val ArtifactFormat: Format[Artifact] = (
    (__ \ "url").format[String] ~
    (__ \ "extract").formatNullable[Boolean].withDefault(true) ~
    (__ \ "executable").formatNullable[Boolean].withDefault(false) ~
    (__ \ "cache").formatNullable[Boolean].withDefault(false)
  ) (Artifact.apply, unlift(Artifact.unapply))

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
  ) (ScheduleSpec.apply, unlift(ScheduleSpec.unapply))

  implicit lazy val ConstraintSpecFormat: Format[ConstraintSpec] = (
    (__ \ "attr").format[String] ~
    (__ \ "op").format[String](filter[String](ValidationError(s"Invalid Operator. Allowed values: ${ConstraintSpec.AvailableOperations}"))(ConstraintSpec.isValidOperation)) ~
    (__ \ "value").formatNullable[String]
  ) (ConstraintSpec.apply, unlift(ConstraintSpec.unapply))

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
  ) (RunSpec.apply, unlift(RunSpec.unapply))

  implicit lazy val JobSpecFormat: Format[JobSpec] = (
    (__ \ "id").format[PathId] ~
    (__ \ "description").format[String] ~
    (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
    (__ \ "schedule").formatNullable[ScheduleSpec] ~
    (__ \ "run").format[RunSpec]
  ) (JobSpec.apply, unlift(JobSpec.unapply))

  implicit lazy val JobRunTaskFormat: Format[JobRunTask] = Json.format[JobRunTask]

  implicit lazy val JobRunIdFormat: Format[JobRunId] = Format(
    Reads.of[String].map(JobRunId(_)),
    Writes[JobRunId] { id => JsString(id.value) }
  )

  implicit lazy val JobRunStatusFormat: Format[JobRunStatus] = new Format[JobRunStatus] {
    override def writes(o: JobRunStatus): JsValue = JsString(JobRunStatus.name(o))

    override def reads(json: JsValue): JsResult[JobRunStatus] = json match {
      case JsString(JobRunStatus(value)) => JsSuccess(value)
      case invalid                       => JsError(s"'$invalid' is not a valid restart policy. Allowed values: ${JobRunStatus.names.keySet}")
    }
  }

  implicit lazy val JobRunWrites: Writes[JobRun] = new Writes[JobRun] {
    override def writes(run: JobRun): JsValue = Json.obj(
      "id" -> run.id,
      "jobSpecId" -> run.jobSpec.id,
      "status" -> run.status,
      "createdAt" -> run.createdAt,
      "finishedAt" -> run.finishedAt,
      "tasks" -> run.tasks
    )
  }

  implicit lazy val StartedJobRunWrites: Writes[StartedJobRun] = new Writes[StartedJobRun] {
    override def writes(o: StartedJobRun): JsValue = JobRunWrites.writes(o.jobRun)
  }

  implicit lazy val JobResultWrites: Writes[JobResult] = Json.writes[JobResult]

  implicit lazy val JobSpecCreatedWrites: Writes[JobSpecCreated] = Json.writes[JobSpecCreated]
  implicit lazy val JobSpecUpdatedWrites: Writes[JobSpecUpdated] = Json.writes[JobSpecUpdated]
  implicit lazy val JobSpecDeletedWrites: Writes[JobSpecDeleted] = Json.writes[JobSpecDeleted]

  implicit lazy val JobRunStartedWrites: Writes[JobRunStarted] = Json.writes[JobRunStarted]
  implicit lazy val JobRunUpdateWrites: Writes[JobRunUpdate] = Json.writes[JobRunUpdate]
  implicit lazy val JobRunFinishedWrites: Writes[JobRunFinished] = Json.writes[JobRunFinished]
  implicit lazy val JobRunFailedWrites: Writes[JobRunFailed] = Json.writes[JobRunFailed]

  lazy val eventWrites: Writes[Event] = new Writes[Event] {
    override def writes(e: Event): JsValue = e match {
      case event: JobSpecCreated => Json.toJson(event)
      case event: JobSpecUpdated => Json.toJson(event)
      case event: JobSpecDeleted => Json.toJson(event)

      case event: JobRunStarted  => Json.toJson(event)
      case event: JobRunUpdate   => Json.toJson(event)
      case event: JobRunFinished => Json.toJson(event)
      case event: JobRunFailed   => Json.toJson(event)
    }
  }
}

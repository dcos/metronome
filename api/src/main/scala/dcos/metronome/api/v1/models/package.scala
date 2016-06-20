package dcos.metronome.api.v1

import dcos.metronome.api._
import dcos.metronome.jobinfo.JobInfo
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model._
import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.PathId
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{ Json, _ }

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.collection.immutable.Seq

package object models {

  import mesosphere.marathon.api.v2.json.Formats.FormatWithDefault

  implicit val errorFormat: Format[ErrorDetail] = Json.format[ErrorDetail]
  implicit val unknownJobsFormat: Format[UnknownJob] = Json.format[UnknownJob]
  implicit val unknownScheduleFormat: Format[UnknownSchedule] = Json.format[UnknownSchedule]
  implicit val unknownJobRunFormat: Format[UnknownJobRun] = Json.format[UnknownJobRun]

  implicit lazy val PathIdFormat: Format[PathId] = Format(
    //TODO: make this functionality available in Marathon
    Reads.of[String](Reads.minLength[String](1)).map(s => PathId(s.split("[.]").toList, absolute = true)),
    Writes[PathId] { id => JsString(id.path.mkString(".")) }
  )

  implicit val DateTimeFormat: Format[DateTime] = Format (
    Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
    Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  )

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
      case JsString(value) if DateTimeZone.getAvailableIDs.asScala.contains(value) =>
        JsSuccess(DateTimeZone.forID(value))
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
      case invalid => JsError(s"'$invalid' is not a valid concurrency policy. " +
        s"Allowed values: ${ConcurrencyPolicy.names}")
    }
  }

  implicit lazy val RestartPolicyFormat: Format[RestartPolicy] = new Format[RestartPolicy] {
    override def writes(o: RestartPolicy): JsValue = JsString(RestartPolicy.name(o))
    override def reads(json: JsValue): JsResult[RestartPolicy] = json match {
      case JsString(RestartPolicy(value)) => JsSuccess(value)
      case invalid => JsError(s"'$invalid' is not a valid restart policy. " +
        s"Allowed values: ${RestartPolicy.names}")
    }
  }

  implicit lazy val ScheduleSpecFormat: Format[ScheduleSpec] = {
    lazy val ScheduleSpecFormatBasic: Format[ScheduleSpec] = (
      (__ \ "id").format[String] ~
      (__ \ "cron").format[CronSpec] ~
      (__ \ "timezone").formatNullable[DateTimeZone].withDefault(ScheduleSpec.DefaultTimeZone) ~
      (__ \ "startingDeadlineSeconds").formatNullable[Duration].withDefault(ScheduleSpec.DefaultStartingDeadline) ~
      (__ \ "concurrencyPolicy").formatNullable[ConcurrencyPolicy].withDefault(ScheduleSpec.DefaultConcurrencyPolicy) ~
      (__ \ "enabled").formatNullable[Boolean].withDefault(ScheduleSpec.DefaultEnabled)
    )(ScheduleSpec.apply, unlift(ScheduleSpec.unapply))

    Format(
      Reads.of[ScheduleSpec](ScheduleSpecFormatBasic),
      new Writes[ScheduleSpec] {
        override def writes(o: ScheduleSpec): JsValue =
          ScheduleSpecFormatBasic.writes(o).as[JsObject] ++
            Json.obj("nextRunAt" -> o.nextExecution(DateTime.now(o.timeZone)))
      }
    )
  }

  implicit lazy val OperatorFormat: Format[Operator] = new Format[Operator] {
    override def writes(o: Operator): JsValue = JsString(Operator.name(o))
    override def reads(json: JsValue): JsResult[Operator] = json match {
      case JsString(Operator(value)) => JsSuccess(value)
      case invalid => JsError(s"'$invalid' is not a valid operator. " +
        s"Allowed values: ${Operator.names}")
    }
  }

  implicit lazy val ConstraintSpecFormat: Format[ConstraintSpec] = (
    (__ \ "attr").format[String] ~
    (__ \ "op").format[Operator] ~
    (__ \ "value").formatNullable[String]
  )(ConstraintSpec.apply, unlift(ConstraintSpec.unapply))

  implicit lazy val PlacementSpecFormat: Format[PlacementSpec] = Json.format[PlacementSpec]

  implicit lazy val ModeFormat: Format[Mode] = new Format[Mode] {
    override def writes(o: Mode): JsValue = JsString(Mode.name(o))
    override def reads(json: JsValue): JsResult[Mode] = json match {
      case JsString(Mode(value)) => JsSuccess(value)
      case invalid               => JsError(s"'$invalid' is not a valid mode. Allowed values: ${Mode.names}")
    }
  }

  implicit lazy val VolumeFormat: Format[Volume] = Json.format[Volume]

  implicit lazy val DockerSpecFormat: Format[DockerSpec] = Json.format[DockerSpec]

  implicit lazy val RestartSpecFormat: Format[RestartSpec] = (
    (__ \ "policy").formatNullable[RestartPolicy].withDefault(RestartSpec.DefaultRestartPolicy) ~
    (__ \ "activeDeadlineSeconds").formatNullable[Duration]
  ) (RestartSpec.apply, unlift(RestartSpec.unapply))

  implicit lazy val RunSpecFormat: Format[JobRunSpec] = (
    (__ \ "cpus").formatNullable[Double].withDefault(JobRunSpec.DefaultCpus) ~
    (__ \ "mem").formatNullable[Double].withDefault(JobRunSpec.DefaultMem) ~
    (__ \ "disk").formatNullable[Double].withDefault(JobRunSpec.DefaultDisk) ~
    (__ \ "cmd").formatNullable[String] ~
    (__ \ "args").formatNullable[Seq[String]] ~
    (__ \ "user").formatNullable[String] ~
    (__ \ "env").formatNullable[Map[String, String]].withDefault(JobRunSpec.DefaultEnv) ~
    (__ \ "placement").formatNullable[PlacementSpec].withDefault(JobRunSpec.DefaultPlacement) ~
    (__ \ "artifacts").formatNullable[Seq[Artifact]].withDefault(JobRunSpec.DefaultArtifacts) ~
    (__ \ "maxLaunchDelay").formatNullable[Duration].withDefault(JobRunSpec.DefaultMaxLaunchDelay) ~
    (__ \ "docker").formatNullable[DockerSpec] ~
    (__ \ "volumes").formatNullable[Seq[Volume]].withDefault(JobRunSpec.DefaultVolumes) ~
    (__ \ "restart").formatNullable[RestartSpec].withDefault(JobRunSpec.DefaultRestartSpec)
  )(JobRunSpec.apply, unlift(JobRunSpec.unapply))

  implicit lazy val JobSpecFormat: Format[JobSpec] = (
    (__ \ "id").format[PathId] ~
    (__ \ "description").formatNullable[String] ~
    (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
    (__ \ "run").format[JobRunSpec]
  )(JobSpec.apply(_, _, _, Seq.empty, _), s => (s.id, s.description, s.labels, s.run))

  implicit lazy val TaskIdFormat: Format[Task.Id] = Format(
    Reads.of[String](Reads.minLength[String](3)).map(Task.Id(_)),
    Writes[Task.Id] { id => JsString(id.idString) }
  )

  implicit lazy val TaskStateFormat: Format[TaskState] = new Format[TaskState] {
    override def writes(o: TaskState): JsValue = JsString(TaskState.name(o))
    override def reads(json: JsValue): JsResult[TaskState] = json match {
      case JsString(TaskState(value)) => JsSuccess(value)
      case invalid => JsError(s"'$invalid' is not a valid task state. " +
        s"Allowed values: ${TaskState.names.keySet}")
    }
  }

  implicit lazy val JobRunTaskFormat: Format[JobRunTask] = Json.format[JobRunTask]

  implicit lazy val JobRunIdFormat: Writes[JobRunId] = Writes[JobRunId] { id => JsString(id.value) }

  implicit lazy val JobRunStatusFormat: Format[JobRunStatus] = new Format[JobRunStatus] {
    override def writes(o: JobRunStatus): JsValue = JsString(JobRunStatus.name(o))
    override def reads(json: JsValue): JsResult[JobRunStatus] = json match {
      case JsString(JobRunStatus(value)) => JsSuccess(value)
      case invalid => JsError(s"'$invalid' is not a valid job run status. " +
        s"Allowed values: ${JobRunStatus.names.keySet}")
    }
  }

  implicit lazy val JobRunWrites: Writes[JobRun] = new Writes[JobRun] {
    override def writes(run: JobRun): JsValue = Json.obj(
      "id" -> run.id,
      "jobId" -> run.jobSpec.id,
      "status" -> run.status,
      "createdAt" -> run.createdAt,
      "finishedAt" -> run.finishedAt,
      "tasks" -> run.tasks.values
    )
  }

  implicit lazy val JobRunInfoWrites: Writes[JobRunInfo] = Json.writes[JobRunInfo]
  implicit lazy val JobHistoryWrites: Writes[JobHistory] = new Writes[JobHistory] {
    override def writes(o: JobHistory): JsValue = Json.obj(
      "successCount" -> o.successCount,
      "failureCount" -> o.failureCount,
      "lastSuccessAt" -> o.lastSuccessAt,
      "lastFailureAt" -> o.lastFailureAt,
      "successfulFinishedRuns" -> o.successfulRuns,
      "failedFinishedRuns" -> o.failedRuns
    )
  }

  implicit lazy val StartedJobRunWrites: Writes[StartedJobRun] = new Writes[StartedJobRun] {
    override def writes(o: StartedJobRun): JsValue = JobRunWrites.writes(o.jobRun)
  }

  implicit lazy val JobResultWrites: Writes[JobResult] = Json.writes[JobResult]

  implicit lazy val JobInfoWrites: Writes[JobInfo] = Json.writes[JobInfo]

  implicit lazy val JsErrorWrites: Writes[JsError] = new Writes[JsError] {
    override def writes(error: JsError): JsValue = Json.obj(
      "message" -> s"Object is not valid",
      "details" -> error.errors.map {
        case (jsPath, errs) => Json.obj("path" -> jsPath.toString(), "errors" -> errs.map(_.message))
      }
    )
  }
}

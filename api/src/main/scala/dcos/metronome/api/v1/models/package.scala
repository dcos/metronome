package dcos.metronome
package api.v1

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import com.typesafe.config.ConfigValue
import dcos.metronome.api._
import dcos.metronome.jobinfo.JobInfo
import dcos.metronome.jobrun.StartedJobRun
import dcos.metronome.model._
import dcos.metronome.scheduler.TaskState
import mesosphere.marathon.SemVer
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.{Parameter, Timestamp}
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.Reads._
import play.api.libs.json.{Json, _}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

package object models {

  import mesosphere.marathon.api.v2.json.Formats.FormatWithDefault

  implicit val errorFormat: Format[ErrorDetail] = Json.format[ErrorDetail]
  implicit val unknownJobsFormat: Format[UnknownJob] = Json.format[UnknownJob]
  implicit val unknownScheduleFormat: Format[UnknownSchedule] = Json.format[UnknownSchedule]
  implicit val unknownJobRunFormat: Format[UnknownJobRun] = Json.format[UnknownJobRun]

  implicit lazy val JobIdFormat: Format[JobId] = Format(
    Reads.of[String](Reads.minLength[String](1)).map(s => JobId(s)),
    Writes[JobId] { id => JsString(id.toString) }
  )

  private val dateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    .withZone(ZoneId.systemDefault())
  implicit val DateTimeFormat: Format[Instant] = Format(
    Reads.instantReads(dateTimeFormatter),
    Writes[Instant] { instant => JsString(dateTimeFormatter.format(instant)) }
  )

  implicit lazy val ArtifactFormat: Format[Artifact] = ((__ \ "uri").format[String] ~
    (__ \ "extract").formatNullable[Boolean].withDefault(true) ~
    (__ \ "executable").formatNullable[Boolean].withDefault(false) ~
    (__ \ "cache").formatNullable[Boolean].withDefault(false))(Artifact.apply, unlift(Artifact.unapply))

  implicit lazy val networkModeFormat: Format[Network.NetworkMode] = Format(
    Reads { json =>
      json.validate[String].flatMap { networkModeName =>
        Network.NetworkMode.byName.get(networkModeName).map(JsSuccess(_)).getOrElse {
          JsError(
            s"${networkModeName} is not a valid network mode; expected one of ${Network.NetworkMode.byName.keys.mkString(",")}"
          )
        }
      }
    },
    Writes({ networkMode: Network.NetworkMode => JsString(networkMode.name) })
  )

  implicit lazy val networkReads: Reads[Network] = ((__ \ "name").readNullable[String] ~
    (__ \ "mode").read[Network.NetworkMode] ~
    (__ \ "labels").readWithDefault[Map[String, String]](Map.empty[String, String]))(Network.apply _)

  implicit lazy val networkWrites: Writes[Network] = new Writes[Network] {
    override def writes(o: Network): JsValue = {
      val b = Seq.newBuilder[(String, JsValueWrapper)]
      o.name.foreach { name =>
        b += ("name" -> name)
      }
      b += "mode" -> o.mode
      if (o.labels.nonEmpty) {
        b += "labels" -> o.labels
      }
      Json.obj(b.result: _*)
    }
  }

  implicit lazy val CronFormat: Format[CronSpec] = new Format[CronSpec] {
    override def writes(o: CronSpec): JsValue = JsString(o.toString)
    override def reads(json: JsValue): JsResult[CronSpec] =
      json match {
        case JsString(CronSpec(value)) => JsSuccess(value)
        case invalid => JsError(s"Can not read cron expression $invalid")
      }
  }

  implicit lazy val DateTimeZoneFormat: Format[ZoneId] = new Format[ZoneId] {
    override def writes(o: ZoneId): JsValue = JsString(o.toString)
    override def reads(json: JsValue): JsResult[ZoneId] =
      json match {
        case JsString(value) if ZoneId.getAvailableZoneIds.asScala.contains(value) =>
          JsSuccess(ZoneId.of(value))
        case invalid => JsError(s"No time zone found with this id: $invalid")
      }
  }

  implicit lazy val DurationFormat: Format[Duration] = new Format[Duration] {
    override def writes(o: Duration): JsValue = JsNumber(o.toSeconds)
    override def reads(json: JsValue): JsResult[Duration] =
      json match {
        case JsNumber(value) if value >= 0 => JsSuccess(value.toLong.seconds)
        case invalid => JsError(s"Can not read duration: $invalid")
      }
  }

  implicit lazy val ConcurrencyPolicyFormat: Format[ConcurrencyPolicy] = new Format[ConcurrencyPolicy] {
    override def writes(o: ConcurrencyPolicy): JsValue = JsString(ConcurrencyPolicy.name(o))
    override def reads(json: JsValue): JsResult[ConcurrencyPolicy] =
      json match {
        case JsString(ConcurrencyPolicy(value)) => JsSuccess(value)
        case invalid =>
          JsError(
            s"'$invalid' is not a valid concurrency policy. " +
              s"Allowed values: ${ConcurrencyPolicy.names}"
          )
      }
  }

  implicit lazy val RestartPolicyFormat: Format[RestartPolicy] = new Format[RestartPolicy] {
    override def writes(o: RestartPolicy): JsValue = JsString(RestartPolicy.name(o))
    override def reads(json: JsValue): JsResult[RestartPolicy] =
      json match {
        case JsString(RestartPolicy(value)) => JsSuccess(value)
        case invalid =>
          JsError(
            s"'$invalid' is not a valid restart policy. " +
              s"Allowed values: ${RestartPolicy.names}"
          )
      }
  }

  implicit lazy val ScheduleSpecFormat: Format[ScheduleSpec] = {
    lazy val ScheduleSpecFormatBasic: Format[ScheduleSpec] = ((__ \ "id").format[String] ~
      (__ \ "cron").format[CronSpec] ~
      (__ \ "timezone").formatNullable[ZoneId].withDefault(ScheduleSpec.DefaultTimeZone) ~
      (__ \ "startingDeadlineSeconds").formatNullable[Duration].withDefault(ScheduleSpec.DefaultStartingDeadline) ~
      (__ \ "concurrencyPolicy").formatNullable[ConcurrencyPolicy].withDefault(ScheduleSpec.DefaultConcurrencyPolicy) ~
      (__ \ "enabled")
        .formatNullable[Boolean]
        .withDefault(ScheduleSpec.DefaultEnabled))(ScheduleSpec.apply, unlift(ScheduleSpec.unapply))

    Format(
      Reads.of[ScheduleSpec](ScheduleSpecFormatBasic),
      new Writes[ScheduleSpec] {
        override def writes(o: ScheduleSpec): JsValue =
          ScheduleSpecFormatBasic.writes(o).as[JsObject] ++
            Json.obj("nextRunAt" -> o.nextExecution())
      }
    )
  }

  implicit lazy val OperatorFormat: Format[Operator] = new Format[Operator] {
    override def writes(o: Operator): JsValue = JsString(o.name)
    override def reads(json: JsValue): JsResult[Operator] =
      json match {
        case JsString(Operator(value)) => JsSuccess(value)
        case invalid =>
          JsError(
            s"'$invalid' is not a valid operator. " +
              s"Allowed values: ${Operator.names}"
          )
      }
  }

  implicit lazy val ConstraintSpecFormat: Format[ConstraintSpec] = ((__ \ "attribute").format[String] ~
    (__ \ "operator").format[Operator] ~
    (__ \ "value").formatNullable[String])(ConstraintSpec.apply, unlift(ConstraintSpec.unapply))

  implicit lazy val PlacementSpecFormat: Format[PlacementSpec] = Json.format[PlacementSpec]

  implicit lazy val ModeFormat: Format[Mode] = new Format[Mode] {
    override def writes(o: Mode): JsValue = JsString(Mode.name(o))
    override def reads(json: JsValue): JsResult[Mode] =
      json match {
        case JsString(Mode(value)) => JsSuccess(value)
        case invalid => JsError(s"'$invalid' is not a valid mode. Allowed values: ${Mode.names}")
      }
  }

  implicit lazy val HostVolumeFormat: Format[HostVolume] = Json.format[HostVolume]

  implicit lazy val SecretVolumeFormat: Format[SecretVolume] = Json.format[SecretVolume]

  implicit lazy val VolumeFormat: Format[Volume] = new Format[Volume] {
    override def writes(o: Volume): JsValue =
      o match {
        case hv: HostVolume => HostVolumeFormat.writes(hv)
        case sv: SecretVolume => SecretVolumeFormat.writes(sv)
      }

    override def reads(json: JsValue): JsResult[Volume] =
      json match {
        case obj: JsObject =>
          obj match {
            case hostVolume if hostVolume.keys == Set("containerPath", "hostPath", "mode") =>
              HostVolumeFormat.reads(hostVolume)
            case secretVolume if secretVolume.keys == Set("containerPath", "secret") =>
              SecretVolumeFormat.reads(secretVolume)
            case invalid =>
              JsError(s"expected HostVolume or SecretVolume object but got $invalid")
          }
        case invalid => JsError(s"expected a Volume object but got $invalid")
      }
  }

  implicit lazy val DockerSpecFormat: Format[DockerSpec] = ((__ \ "image").format[String] ~
    (__ \ "privileged").formatNullable[Boolean].withDefault(false) ~
    (__ \ "parameters").formatNullable[Seq[Parameter]].withDefault(DockerSpec.DefaultParameters) ~
    (__ \ "forcePullImage").formatNullable[Boolean].withDefault(false))(DockerSpec.apply, unlift(DockerSpec.unapply))

  implicit lazy val UcrFormatSpec: Format[UcrSpec] = ((__ \ "image").format[ImageSpec] ~
    (__ \ "privileged").formatNullable[Boolean].withDefault(false))(UcrSpec.apply, unlift(UcrSpec.unapply))

  implicit lazy val ParameterWrites: Writes[mesosphere.marathon.state.Parameter] =
    new Writes[mesosphere.marathon.state.Parameter] {
      override def writes(param: mesosphere.marathon.state.Parameter): JsValue =
        Json.obj("key" -> param.key, "value" -> param.value)
    }

  val ParameterReads: Reads[Parameter] = ((JsPath \ "key").read[String] and
    (JsPath \ "value").read[String])((k, v) => mesosphere.marathon.state.Parameter(k, v))

  implicit lazy val ParameterFormat: Format[mesosphere.marathon.state.Parameter] =
    Format(ParameterReads, ParameterWrites)

  implicit lazy val ImageSpecFormat: Format[ImageSpec] = ((__ \ "id").format[String] ~
    (__ \ "kind").formatNullable[String].withDefault(ImageSpec.DefaultKind) ~
    (__ \ "forcePull").formatNullable[Boolean].withDefault(false))(ImageSpec.apply, unlift(ImageSpec.unapply))

  implicit lazy val RestartSpecFormat: Format[RestartSpec] =
    ((__ \ "policy").formatNullable[RestartPolicy].withDefault(RestartSpec.DefaultRestartPolicy) ~
      (__ \ "activeDeadlineSeconds").formatNullable[Duration])(RestartSpec.apply, unlift(RestartSpec.unapply))

  implicit lazy val FiniteDurationFormat: Format[FiniteDuration] = new Format[FiniteDuration] {
    override def writes(o: FiniteDuration): JsValue = JsNumber(o.toSeconds)
    override def reads(json: JsValue): JsResult[FiniteDuration] =
      json match {
        case JsNumber(value) => JsSuccess(Duration(value.toLong, SECONDS))
        case invalid => JsError(s"'$invalid' is not a valid duration. ")
      }
  }

  implicit lazy val RunSpecFormat: Format[JobRunSpec] =
    ((__ \ "cpus").formatNullable[Double].withDefault(JobRunSpec.DefaultCpus) ~
      (__ \ "mem").formatNullable[Double].withDefault(JobRunSpec.DefaultMem) ~
      (__ \ "disk").formatNullable[Double].withDefault(JobRunSpec.DefaultDisk) ~
      (__ \ "gpus").formatNullable[Int].withDefault(JobRunSpec.DefaultGpus) ~
      (__ \ "cmd").formatNullable[String] ~
      (__ \ "args").formatNullable[Seq[String]] ~
      (__ \ "user").formatNullable[String] ~
      (__ \ "env").formatNullable[Map[String, EnvVarValueOrSecret]].withDefault(JobRunSpec.DefaultEnv) ~
      (__ \ "placement").formatNullable[PlacementSpec].withDefault(JobRunSpec.DefaultPlacement) ~
      (__ \ "artifacts").formatNullable[Seq[Artifact]].withDefault(JobRunSpec.DefaultArtifacts) ~
      (__ \ "maxLaunchDelay").formatNullable[Duration].withDefault(JobRunSpec.DefaultMaxLaunchDelay) ~
      (__ \ "docker").formatNullable[DockerSpec] ~
      (__ \ "ucr").formatNullable[UcrSpec] ~
      (__ \ "volumes").formatNullable[Seq[Volume]].withDefault(JobRunSpec.DefaultVolumes) ~
      (__ \ "restart").formatNullable[RestartSpec].withDefault(JobRunSpec.DefaultRestartSpec) ~
      (__ \ "taskKillGracePeriodSeconds").formatNullable[FiniteDuration] ~
      (__ \ "secrets").formatNullable[Map[String, SecretDef]].withDefault(JobRunSpec.DefaultSecrets) ~
      (__ \ "networks")
        .formatNullable[Seq[Network]]
        .withDefault(JobRunSpec.DefaultNetworks))(JobRunSpec.apply, unlift(JobRunSpec.unapply))

  // A format for JobId that writes and object {"id": value} instead of a plain string.
  val DependencyFormat: Format[Seq[JobId]] = Format(
    Reads.seq((__ \ "id").read[JobId]).map(_.toVector),
    Writes.iterableWrites[JobId, Seq]((__ \ "id").write[JobId])
  )

  implicit lazy val JobSpecFormat: Format[JobSpec] = ((__ \ "id").format[JobId] ~
    (__ \ "description").formatNullable[String] ~
    (__ \ "dependencies").formatNullable[Seq[JobId]](DependencyFormat).withDefault(JobSpec.DefaultDependencies) ~
    (__ \ "labels").formatNullable[Map[String, String]].withDefault(Map.empty) ~
    (__ \ "schedules").formatNullable[Seq[ScheduleSpec]].withDefault(JobSpec.DefaultSchedules) ~
    (__ \ "run")
      .format[JobRunSpec])(JobSpec.apply, unlift(JobSpec.unapply))

  implicit lazy val TaskIdFormat: Format[Task.Id] = Format(
    Reads.of[String](Reads.minLength[String](3)).map(Task.Id(_)),
    Writes[Task.Id] { id => JsString(id.idString) }
  )

  implicit lazy val TaskStateFormat: Format[TaskState] = new Format[TaskState] {
    override def writes(o: TaskState): JsValue = JsString(TaskState.name(o))
    override def reads(json: JsValue): JsResult[TaskState] =
      json match {
        case JsString(TaskState(value)) => JsSuccess(value)
        case invalid =>
          JsError(
            s"'$invalid' is not a valid task state. " +
              s"Allowed values: ${TaskState.names.keySet}"
          )
      }
  }

  implicit lazy val JobRunTaskFormat: Format[JobRunTask] = Json.format[JobRunTask]

  implicit lazy val JobRunIdFormat: Writes[JobRunId] = Writes[JobRunId] { id => JsString(id.value) }

  implicit lazy val JobRunStatusFormat: Format[JobRunStatus] = new Format[JobRunStatus] {
    override def writes(o: JobRunStatus): JsValue = JsString(JobRunStatus.name(o))
    override def reads(json: JsValue): JsResult[JobRunStatus] =
      json match {
        case JsString(JobRunStatus(value)) => JsSuccess(value)
        case invalid =>
          JsError(
            s"'$invalid' is not a valid job run status. " +
              s"Allowed values: ${JobRunStatus.names.keySet}"
          )
      }
  }

  implicit lazy val JobRunWrites: Writes[JobRun] = new Writes[JobRun] {
    override def writes(run: JobRun): JsValue =
      Json.obj(
        "id" -> run.id,
        "jobId" -> run.jobSpec.id,
        "status" -> run.status,
        "createdAt" -> run.createdAt,
        "completedAt" -> run.completedAt,
        "tasks" -> run.tasks.values
      )
  }

  implicit lazy val JobRunInfoWrites: Writes[JobRunInfo] = Json.writes[JobRunInfo]
  implicit lazy val JobHistoryWrites: Writes[JobHistory] = new Writes[JobHistory] {
    override def writes(o: JobHistory): JsValue =
      Json.obj(
        "successCount" -> o.successCount,
        "failureCount" -> o.failureCount,
        "lastSuccessAt" -> o.lastSuccessAt,
        "lastFailureAt" -> o.lastFailureAt,
        "successfulFinishedRuns" -> o.successfulRuns,
        "failedFinishedRuns" -> o.failedRuns
      )
  }
  implicit lazy val JobHistorySummaryWrites: Writes[JobHistorySummary] = new Writes[JobHistorySummary] {
    override def writes(o: JobHistorySummary): JsValue =
      Json.obj(
        "successCount" -> o.successCount,
        "failureCount" -> o.failureCount,
        "lastSuccessAt" -> o.lastSuccessAt,
        "lastFailureAt" -> o.lastFailureAt
      )
  }

  implicit lazy val StartedJobRunWrites: Writes[StartedJobRun] = new Writes[StartedJobRun] {
    override def writes(o: StartedJobRun): JsValue = JobRunWrites.writes(o.jobRun)
  }

  implicit lazy val JobResultWrites: Writes[JobResult] = Json.writes[JobResult]

  implicit lazy val JobInfoWrites: Writes[JobInfo] = { jobInfo: JobInfo =>
    val fields = Json.obj(
      "id" -> jobInfo.id,
      "description" -> jobInfo.description,
      "dependencies" -> DependencyFormat.writes(jobInfo.dependencies),
      "labels" -> jobInfo.labels,
      "run" -> jobInfo.run,
      "schedules" -> jobInfo.schedules,
      "activeRuns" -> jobInfo.activeRuns,
      "history" -> jobInfo.history,
      "historySummary" -> jobInfo.historySummary
    ).fields.filterNot(_._2 == JsNull)

    JsObject(fields)
  }

  implicit lazy val JsErrorWrites: Writes[JsError] = new Writes[JsError] {
    override def writes(error: JsError): JsValue =
      Json.obj(
        "message" -> s"Object is not valid",
        "details" -> error.errors.map {
          case (jsPath, errs) => Json.obj("path" -> jsPath.toString(), "errors" -> errs.map(_.message))
        }
      )
  }

  implicit lazy val SemVerWrites: Writes[SemVer] = new Writes[SemVer] {
    override def writes(o: SemVer): JsValue = JsString(o.toString())
  }

  implicit lazy val ConfigValueWrites: Writes[ConfigValue] = new Writes[ConfigValue] {
    override def writes(o: ConfigValue): JsValue = JsString(o.render())
  }

  implicit lazy val JobsConfigWrites: Writes[JobsConfig] = new Writes[JobsConfig] {
    override def writes(o: JobsConfig): JsValue = {
      o.configSet.foldLeft(Json.obj()) {
        case (acc, (key, value)) => acc.+(key -> Json.toJson(value))
      }
    }
  }

  implicit lazy val MetronomeInfoWrites: Writes[MetronomeInfo] = new Writes[MetronomeInfo] {
    override def writes(o: MetronomeInfo): JsValue = {
      Json.obj("version" -> o.version, "libVersion" -> o.libVersion, "config" -> o.config)
    }
  }

  val LeaderInfoWrites: Writes[LeaderInfo] = Json.writes[LeaderInfo]

  implicit lazy val TimestampWrites: Writes[Timestamp] = new Writes[Timestamp] {
    override def writes(timestamp: Timestamp): JsValue = {
      JsString(timestamp.toString())
    }
  }

  implicit lazy val QueuedTaskInfoWrites: Writes[QueuedJobRunInfo] = new Writes[QueuedJobRunInfo] {
    //    output of queue is only runid
    override def writes(runInfo: QueuedJobRunInfo): JsValue = Json.obj("runId" -> JsString(runInfo.runId))
  }

  def queuedTaskInfoMap(job: String, queuedTaskList: Seq[QueuedJobRunInfo]): JsValue = {
    Json.obj("jobId" -> job, "runs" -> queuedTaskList)
  }

  implicit lazy val QueuedJobRunMapWrites: Writes[Map[String, Seq[QueuedJobRunInfo]]] =
    new Writes[Map[String, Seq[QueuedJobRunInfo]]] {
      override def writes(taskInfoMap: Map[String, Seq[QueuedJobRunInfo]]): JsValue = {
        Json.toJson(taskInfoMap.map { case (k, v) => queuedTaskInfoMap(k, v) })
      }
    }
}

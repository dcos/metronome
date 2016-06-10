package dcos.metronome.utils.glue

import java.util.concurrent.TimeUnit

import dcos.metronome.model._
import mesosphere.marathon
import mesosphere.marathon.core.readiness.ReadinessCheck
import mesosphere.marathon.health.HealthCheck
import mesosphere.marathon.state.{ AppDefinition, Container, EnvVarValue, FetchUri, PathId, PortDefinition, Secret, UpgradeStrategy }

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.language.implicitConversions
/**
  * Temporary object containing implicit conversions to and from Marathon code. Should be removed eventually.
  */
object MarathonImplicits {

  implicit def jobRunToAppDefinition(run: JobRun): AppDefinition = {
    val jobSpec = run.jobSpec

    AppDefinition(
      id = PathId.fromSafePath(run.id.toString), // FIXME (glue): JobRunId#attempt
      cmd = jobSpec.run.cmd,
      args = jobSpec.run.args,
      user = jobSpec.run.user,
      env = EnvVarValue(jobSpec.run.env),
      instances = 1,
      cpus = jobSpec.run.cpus,
      mem = jobSpec.run.mem,
      disk = jobSpec.run.disk,
      executor = "//cmd",
      constraints = jobSpec.run.placement.constraints.toSet.map(constraintSpec2ProtosConstraint),
      fetch = jobSpec.run.artifacts.map(artifact2FetchUri),
      storeUrls = Seq.empty[String],
      portDefinitions = Seq.empty[PortDefinition],
      requirePorts = false,
      backoff = 0.seconds,
      backoffFactor = 0.0,
      maxLaunchDelay = FiniteDuration(jobSpec.run.maxLaunchDelay.toMillis, TimeUnit.MILLISECONDS),
      container = deriveContainer(jobSpec),
      healthChecks = Set.empty[HealthCheck],
      readinessChecks = Seq.empty[ReadinessCheck],
      taskKillGracePeriod = None, // FIXME (glue): add
      dependencies = Set.empty[PathId],
      upgradeStrategy = UpgradeStrategy(minimumHealthCapacity = 0.0, maximumOverCapacity = 1.0),
      labels = jobSpec.labels,
      acceptedResourceRoles = None, // FIXME (glue): will we support this in v1?
      ipAddress = None, // FIXME (glue): will we support this in v1?
      versionInfo = AppDefinition.VersionInfo.NoVersion, // FIXME (glue): does this make sense?
      residency = None,
      secrets = Map.empty[String, Secret]
    )
  }

  private[this] def constraintSpec2ProtosConstraint(spec: ConstraintSpec): marathon.Protos.Constraint = {
    val marathonOperator = spec.operator match {
      case Operator.Eq     => marathon.Protos.Constraint.Operator.CLUSTER
      case Operator.Like   => marathon.Protos.Constraint.Operator.LIKE
      case Operator.Unlike => marathon.Protos.Constraint.Operator.UNLIKE
    }

    val builder = marathon.Protos.Constraint.newBuilder()
      .setOperator(marathonOperator)
      .setField(spec.attribute)
    spec.value.foreach(builder.setValue)
    builder.build()
  }

  // FIXME (glue): add generic interface in Marathon library
  private[this] def artifact2FetchUri(artifact: Artifact): FetchUri = {
    FetchUri(
      uri = artifact.uri,
      extract = artifact.extract,
      executable = artifact.executable,
      cache = artifact.cache
    )
  }

  private[this] def deriveContainer(jobSpec: JobSpec): Option[Container] = {
    // FIXME (glue): correctly implement this
    None // default, will setup TaskInfo to use the Mesos containerizer
  }

  implicit def string2ConstraintOperator(str: String): marathon.Protos.Constraint.Operator = {
    marathon.Protos.Constraint.Operator.valueOf(str)
  }
}

package dcos.metronome.model

import com.wix.accord.Validator

// TODO: in order to help users to process specific data, it might be helpful to support overriding artifacts
// TODO: decide whether we want to promote the variable names that can or need to be provided
case class JobRunSpecOverrides(
  env:       Map[String, EnvVarValueOrSecret] = JobRunSpec.DefaultEnv,
  placement: PlacementSpec                    = JobRunSpec.DefaultPlacement)

object JobRunSpecOverrides {

  val empty: JobRunSpecOverrides = new JobRunSpecOverrides() {
    override def toString: String = "JobRunSpecOverrides.empty"
  }

  def apply(
    env:       Map[String, EnvVarValueOrSecret] = JobRunSpec.DefaultEnv,
    placement: PlacementSpec                    = JobRunSpec.DefaultPlacement): JobRunSpecOverrides = {
    if (env == JobRunSpec.DefaultEnv && placement == JobRunSpec.DefaultPlacement) empty
    else new JobRunSpecOverrides(env, placement)
  }

  implicit lazy val validJobRunOverrideSpec: Validator[JobRunSpecOverrides] = new Validator[JobRunSpecOverrides] {
    import com.wix.accord._

    override def apply(spec: JobRunSpecOverrides): Result = Success
  }
}

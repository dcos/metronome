package dcos.metronome

import mesosphere.marathon.SemVer

case class MetronomeInfo(
  version:    String,
  libVersion: SemVer,
  config:     JobsConfig)

object MetronomeInfo {
  def apply(config: JobsConfig): MetronomeInfo =
    MetronomeInfo(MetronomeBuildInfo.version, MetronomeBuildInfo.marathonVersion, config)
}

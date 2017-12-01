package dcos.metronome

case object MetronomeInfoBuilder {

  lazy val metronomeInfo: MetronomeInfo = {
    MetronomeInfo(MetronomeBuildInfo.version, MetronomeBuildInfo.marathonVersion)
  }
}

package dcos.metronome

/**
  * Creates the MetronomeInfo object for reporting at the /info endpoint.   It currently only provides
  * build information, however it will likely be extended to include configuration information similar to Marathon.
  */
case object MetronomeInfoBuilder {

  lazy val metronomeInfo: MetronomeInfo = {
    MetronomeInfo(MetronomeBuildInfo.version, MetronomeBuildInfo.marathonVersion)
  }
}

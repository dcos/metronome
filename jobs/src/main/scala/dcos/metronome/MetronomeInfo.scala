package dcos.metronome

import dcos.metronome.utils.SemVer

case class MetronomeInfo(
  version:    String,
  libVersion: SemVer)


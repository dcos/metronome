package dcos.metronome

import mesosphere.marathon.SemVer

case class MetronomeInfo(version: String, libVersion: SemVer)

case class LeaderInfo(leader: String)

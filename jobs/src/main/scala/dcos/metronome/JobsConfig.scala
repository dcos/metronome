package dcos.metronome

import dcos.metronome.jobrun.JobRunConfig
import dcos.metronome.jobspec.JobSpecConfig
import mesosphere.marathon.AllConf

trait JobsConfig extends JobRunConfig with JobSpecConfig {

  def scallopConf: AllConf

}

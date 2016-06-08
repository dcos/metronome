package dcos.metronome

import dcos.metronome.history.JobHistoryConfig
import dcos.metronome.jobrun.JobRunConfig
import dcos.metronome.jobspec.JobSpecConfig
import mesosphere.marathon.AllConf

trait JobsConfig extends JobRunConfig with JobSpecConfig with JobHistoryConfig {

  def scallopConf: AllConf

}

package dcos.metronome

import dcos.metronome.history.HistoryConfig
import dcos.metronome.jobrun.JobRunConfig
import dcos.metronome.jobspec.JobSpecConfig
import mesosphere.marathon.AllConf

trait JobsConfig extends HistoryConfig with JobRunConfig with JobSpecConfig {

  def scallopConf: AllConf

}

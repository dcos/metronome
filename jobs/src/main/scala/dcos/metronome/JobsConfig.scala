package dcos.metronome

import dcos.metronome.greeting.GreetingConf
import dcos.metronome.history.HistoryConfig
import dcos.metronome.jobrun.JobRunConfig
import dcos.metronome.jobspec.JobSpecConfig
import mesosphere.marathon.AllConf

trait JobsConfig extends GreetingConf with HistoryConfig with JobRunConfig with JobSpecConfig {

  def scallopConf: AllConf

}

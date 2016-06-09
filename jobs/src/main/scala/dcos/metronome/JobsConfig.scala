package dcos.metronome

import dcos.metronome.history.JobHistoryConfig
import dcos.metronome.jobrun.JobRunConfig
import dcos.metronome.jobspec.JobSpecConfig
import dcos.metronome.scheduler.SchedulerConfig
import mesosphere.marathon.AllConf

trait JobsConfig extends JobRunConfig with JobSpecConfig with JobHistoryConfig with SchedulerConfig {

  def scallopConf: AllConf

}

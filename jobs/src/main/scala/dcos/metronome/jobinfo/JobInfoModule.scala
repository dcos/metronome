package dcos.metronome.jobinfo

import dcos.metronome.behavior.Behavior
import dcos.metronome.jobinfo.impl.JobInfoServiceImpl
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService

class JobInfoModule(jobSpecService: JobSpecService, jobRunService: JobRunService, behavior: Behavior) {

  import com.softwaremill.macwire._

  lazy val jobInfoService: JobInfoService = behavior(wire[JobInfoServiceImpl])

}

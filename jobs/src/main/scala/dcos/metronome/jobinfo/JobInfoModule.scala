package dcos.metronome
package jobinfo

import dcos.metronome.behavior.Behavior
import dcos.metronome.history.JobHistoryService
import dcos.metronome.jobinfo.impl.JobInfoServiceImpl
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService

class JobInfoModule(
    jobSpecService: JobSpecService,
    jobRunService:  JobRunService,
    behavior:       Behavior, history: JobHistoryService
) {

  import com.softwaremill.macwire._

  lazy val jobInfoService: JobInfoService = behavior(wire[JobInfoServiceImpl])

}

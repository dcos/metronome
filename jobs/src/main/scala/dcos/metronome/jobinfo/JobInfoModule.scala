package dcos.metronome
package jobinfo

import dcos.metronome.history.JobHistoryService
import dcos.metronome.jobinfo.impl.JobInfoServiceImpl
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService

class JobInfoModule(
  jobSpecService: JobSpecService,
  jobRunService:  JobRunService, history: JobHistoryService) {

  import com.softwaremill.macwire._

  lazy val jobInfoService: JobInfoService = wire[JobInfoServiceImpl]

}

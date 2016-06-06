package dcos.metronome.jobinfo

import dcos.metronome.jobinfo.impl.JobInfoServiceImpl
import dcos.metronome.jobrun.JobRunService
import dcos.metronome.jobspec.JobSpecService

class JobInfoModule(jobSpecService: JobSpecService, jobRunService: JobRunService) {

  lazy val jobInfoService: JobInfoService = new JobInfoServiceImpl(jobSpecService, jobRunService)

}

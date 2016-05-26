package dcos.metronome.repository

import dcos.metronome.model.{ JobRunId, JobRun, JobSpec }
import mesosphere.marathon.state.PathId

class RepositoryModule {

  def jobSpecRepository: Repository[PathId, JobSpec] = ???

  def jobRunRepository: Repository[JobRunId, JobRun] = ???

}

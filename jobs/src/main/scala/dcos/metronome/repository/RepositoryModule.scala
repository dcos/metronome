package dcos.metronome.repository

import dcos.metronome.model.{ JobHistory, JobRunId, JobRun, JobSpec }
import dcos.metronome.repository.impl.InMemoryRepository
import mesosphere.marathon.state.PathId

class RepositoryModule {

  def jobSpecRepository: Repository[PathId, JobSpec] = new InMemoryRepository[PathId, JobSpec]

  def jobRunRepository: Repository[JobRunId, JobRun] = new InMemoryRepository[JobRunId, JobRun]

  def jobHistoryRepository: Repository[PathId, JobHistory] = new InMemoryRepository[PathId, JobHistory]
}


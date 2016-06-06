package dcos.metronome.jobinfo

import dcos.metronome.model.JobSpec

/**
  * A JobSpecSelector is a simple filter to match jobs.
  */
trait JobSpecSelector {
  def matches(jobSpec: JobSpec): Boolean
}

object JobSpecSelector {
  def apply(matchesFunc: JobSpec => Boolean): JobSpecSelector = new JobSpecSelector {
    override def matches(jobSpec: JobSpec): Boolean = matchesFunc(jobSpec)
  }

  def all: JobSpecSelector = JobSpecSelector(_ => true)

  def forall(selectors: Iterable[JobSpecSelector]): JobSpecSelector = new AllJobSpecSelectorsMustMatch(selectors)

  private[jobinfo] class AllJobSpecSelectorsMustMatch(selectors: Iterable[JobSpecSelector]) extends JobSpecSelector {
    override def matches(jobSpec: JobSpec): Boolean = selectors.forall(_.matches(jobSpec))
  }
}

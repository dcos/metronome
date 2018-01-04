package dcos.metronome
package scheduler

/** Wraps operations that shall be periodically scheduled  during leadership */
trait PeriodicOperations {

  /** Schedule periodic operations (triggered when we gain leadership) */
  def schedule(): Unit

  /** Cancel all scheduled periodic operations (triggered if we loose leadership) */
  def cancel(): Unit
}

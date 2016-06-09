package dcos.metronome.scheduler.impl

import java.util.Timer

import dcos.metronome.scheduler.PeriodicOperations

class PeriodicOperationsImpl extends PeriodicOperations {

  private[this] def newTimer() = new Timer("periodicOperationsTimer")
  private[this] var timer = newTimer()

  /** Initialize timers for periodic operations (triggered when we gain leadership) */
  override def schedule(): Unit = synchronized {
    timer = newTimer()

    // schedule any periodic operations here ...
  }

  /** Cancel periodic scheduling of operations (triggered if we loose leadership or shutdown) */
  override def cancel(): Unit = synchronized {
    timer.cancel()
  }
}

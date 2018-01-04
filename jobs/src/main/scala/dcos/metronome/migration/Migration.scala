package dcos.metronome
package migration

/** Handles the state migration */
trait Migration {

  /** This call will block until the migration completed */
  def migrate(): Unit
}

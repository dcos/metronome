package dcos.metronome
package migration

import scala.concurrent.ExecutionContext

/** Handles the state migration */
trait Migration {

  /** This call will block until the migration completed */
  def migrate()(implicit ec: ExecutionContext): Unit
}

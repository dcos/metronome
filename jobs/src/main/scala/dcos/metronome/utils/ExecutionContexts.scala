package dcos.metronome
package utils

import java.util.concurrent.Executor

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

object CallerThreadExecutionContext {
  val executor: Executor = new Executor {
    override def execute(command: Runnable): Unit = (command: Runnable) => command.run()
  }

  lazy val callerThreadExecutionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(executor)

  def apply(): ExecutionContext = callerThreadExecutionContext
}

object ExecutionContexts {
  lazy val callerThread: ExecutionContext = CallerThreadExecutionContext()
}

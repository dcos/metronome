// A collection of pipeline utilities such as stage names and colors.

import ammonite.ops._
import ammonite.ops.ImplicitWd._
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import $file.provision

val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

def ciLogFile(name: String): File = {
  val log = new File(name)
  if (!log.exists())
    log.createNewFile()
  log
}

// Color definitions
object Colors {
  val BrightRed = "\u001b[31;1m"
  val BrightGreen = "\u001b[32;1m"
  val BrightBlue = "\u001b[34;1m"
  val Reset = "\u001b[0m"
}

def printWithColor(text: String, color: String): Unit = {
  print(color)
  print(text)
  print(Colors.Reset)
}

def printlnWithColor(text: String, color: String): Unit = printWithColor(s"$text\n", color)

def printHr(color: String, character: String = "*", length: Int = 80): Unit = {
  printWithColor(s"${character * length}\n", color)
}

def printCurrentTime() = {
  val date = LocalDateTime.now()
  printWithColor(s"Started at: ${date.format(timeFormatter)}\n", Colors.BrightBlue)
}

def printStageTitle(name: String): Unit = {
  val indent = (80 - name.length) / 2
  print("\n")
  print(" " * indent)
  printWithColor(s"$name\n", Colors.BrightBlue)
  printHr(Colors.BrightBlue)
  printCurrentTime()
}

case class BuildException(val cmd: String, val exitValue: Int, private val cause: Throwable = None.orNull)
  extends Exception(s"'$cmd' exited with $exitValue", cause)
case class StageException(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)
def stage[T](name: String)(block: => T): T = {
  printStageTitle(name)

  try {
    block
  }
  catch { case NonFatal(e) =>
    throw new StageException(s"Stage $name failed.", e)
  }
}

/**
 * Run a process with given commands and time out it runs too long.
 *
 * @param timeout The maximum time to wait.
 * @param logFileName Name of file which collects all logs.
 * @param commands The commands that are executed in a process. E.g. "sbt",
 *  "compile".
 */
def runWithTimeout(timeout: FiniteDuration, logFileName: String)(commands: Seq[String]): Unit = {

  val builder = new java.lang.ProcessBuilder()
  val buildProcess = builder
    .directory(new java.io.File(pwd.toString))
    .command(commands.asJava)
    .inheritIO()
    .redirectOutput(ProcessBuilder.Redirect.appendTo(ciLogFile(logFileName)))
    .start()

    val exited = buildProcess.waitFor(timeout.length, timeout.unit)

    if (exited) {
      val exitValue = buildProcess.exitValue
      if(buildProcess.exitValue != 0) {
        val cmd = commands.mkString(" ")
        throw new utils.BuildException(cmd, exitValue)
      }
    } else {
      // The process timed out. Try to kill it.
      buildProcess.destroyForcibly().waitFor()
      val cmd = commands.mkString(" ")
      throw new java.util.concurrent.TimeoutException(s"'$cmd' timed out after $timeout.")
    }
}

def withCleanUp[T](block: => T): T = {
  try { block }
  finally { provision.killStaleTestProcesses() }
}

/**
 * @return True if build is on master build.
 */
def isMasterBuild(): Boolean = {
  sys.env.get("JOB_NAME").contains("marathon-pipelines/master")
}

/**
 * @return True if build is for pull request.
 */
def isPullRequest(): Boolean = {
  val pr = """marathon-pipelines/PR-(\d+)""".r
  sys.env.get("JOB_NAME").collect { case pr(_) => true }.getOrElse(false)
}

def priorPatchVersion(tag: String): Option[String] = {
  val Array(major, minor, patch) = tag.replace("v", "").split('.').take(3).map(_.toInt)
  if (patch == 0)
    None
  else
    Some(s"v${major}.${minor}.${patch - 1}")
}

def escapeCmdArg(cmd: String): String = {
  val subbed = cmd.replace("'", "\\'").replace("\n", "\\n")
  s"""$$'${subbed}'"""
}

package dcos.metronome

import java.util.jar.{ Attributes, Manifest }

import mesosphere.marathon.io.IO

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

/**
  * Provides build information details regarding Metronome and Marathon at runtime.
  */
case object MetronomeBuildInfo {
  private val metronomeJar = "\\bdcos\\.jobs-[0-9.]+".r

  private lazy val devBuildVersion = {
    // parsing version in ThisBuild := "0.3.0"
    val version: String = Try[String](Source.fromFile("version.sbt").mkString.split(":= ") {
      1
    }) match {
      case Success(v) => v.replace("\"", "").trim
      case Failure(_) => "0.0.0"
    }
    version
  }

  /**
    * sbt-native-package provides all of the files as individual JARs. By default, `getResourceAsStream` returns the
    * first matching file for the first JAR in the class path. Instead, we need to enumerate through all of the
    * manifests, and find the one that applies to the Metronome application jar.
    */
  lazy val manifestPath: List[java.net.URL] =
    getClass.getClassLoader.getResources("META-INF/MANIFEST.MF").asScala.filter { manifest =>
      metronomeJar.findFirstMatchIn(manifest.getPath).nonEmpty
    }.toList

  lazy val manifest: Option[Manifest] = manifestPath match {
    case Nil => None
    case List(file) =>
      val mf = new Manifest()
      IO.using(file.openStream) { f =>
        mf.read(f)
        Some(mf)
      }
    case otherwise =>
      throw new RuntimeException(s"Multiple metronome JAR manifests returned! $otherwise")
  }

  lazy val attributes: Option[Attributes] = manifest.map(_.getMainAttributes())

  def getAttribute(name: String): Option[String] = attributes.flatMap { attrs =>
    try {
      Option(attrs.getValue(name))
    } catch {
      case NonFatal(_) => None
    }
  }

  // IntelliJ has its own manifest.mf that will inject a version that doesn't necessarily match
  // our actual version. This can cause Migrations to fail since the version number doesn't correctly match up.
  lazy val version: String = getAttribute("Implementation-Version").getOrElse(devBuildVersion)

  lazy val scalaVersion: String = getAttribute("Scala-Version").getOrElse("2.x.x")

  lazy val marathonVersion: mesosphere.marathon.SemVer = MarathonBuildInfo.version

}

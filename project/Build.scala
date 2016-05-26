import play.sbt.{ PlayLayoutPlugin, PlayScala }
import sbt.Keys._
import sbt._
import sbtprotobuf.{ ProtobufPlugin => PB }
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._


object Build extends sbt.Build {

  lazy val metronome = Project(
    id = "metronome",
    base = file("."),
    dependencies = Seq(api, jobs),
    settings = baseSettings ++ formatSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy
      )
    ) ++ PB.protobufSettings
  ).aggregate(api, jobs).enablePlugins(PlayScala).disablePlugins(PlayLayoutPlugin)

  lazy val api = Project(
    id = "api",
    base = file("api"),
    dependencies = Seq(jobs),
    settings = baseSettings ++ formatSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.playJson,
        Dependency.marathonPlugin,
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy,
        Dependency.yaml,
        Dependency.cronUtils,
        Dependency.Test.scalatest,
        Dependency.Test.scalatestPlay
      )
    )
  ).enablePlugins(PlayScala).disablePlugins(PlayLayoutPlugin)

  lazy val jobs = Project(
    id = "jobs",
    base = file("jobs"),
    settings = baseSettings ++ formatSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.playJson,
        Dependency.marathon,
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy,
        Dependency.cronUtils
      )
    )
  )

  lazy val baseSettings = Seq(
    organization := "dcos",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq(scalaVersion.value),
    scalacOptions in Compile ++= Seq(
      "-encoding", "UTF-8",
      "-target:jvm-1.8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen"
    ),
    javacOptions in Compile ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
    resolvers ++= Seq(
      "Mesosphere Public Repo" at "http://downloads.mesosphere.io/maven",
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
      "Spray Maven Repository" at "http://repo.spray.io/"
    ),
    fork in Test := true
  )

  lazy val formatSettings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(AlignParameters, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(SpacesAroundMultiImports, true)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)
  )


  object Dependency {
    object V {
      // Test deps versions
      val ScalaTest = "2.1.7"
      val MacWire = "2.2.2"
      val Marathon = "2.0.0-SNAPSHOT"
      val Play = "2.5.3"
      val CronUtils = "3.1.6"
      val WixAccord = "0.5"
    }

    val playJson = "com.typesafe.play" %% "play-json" % V.Play
    val yaml = "net.jcazevedo" %% "moultingyaml" % "0.2"
    val macWireMacros = "com.softwaremill.macwire" %% "macros" % V.MacWire % "provided"
    val macWireUtil = "com.softwaremill.macwire" %% "util" % V.MacWire
    val macWireProxy = "com.softwaremill.macwire" %% "proxy" % V.MacWire
    val marathon = "dcos.marathon" %% "marathon" % V.Marathon exclude("com.typesafe.play", "play-json") exclude("mesosphere.marathon", "ui") exclude("mesosphere.marathon", "ui") exclude("mesosphere", "chaos") exclude("org.apache.hadoop", "hadoop-hdfs") exclude("org.apache.hadoop", "hadoop-common") exclude("org.eclipse.jetty", "jetty-servlets")
    val marathonPlugin = "dcos.marathon" %% "plugin-interface" % V.Marathon
    val cronUtils = "com.cronutils" % "cron-utils" % V.CronUtils
    val wixAccord = "com.wix" %% "accord-core" % V.WixAccord

    object Test {
      val scalatest = "org.scalatest" %% "scalatest" % V.ScalaTest % "test"
      val scalatestPlay = "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % "test"
    }
  }
}

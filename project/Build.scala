import com.amazonaws.auth.{InstanceProfileCredentialsProvider, EnvironmentVariableCredentialsProvider}
import com.typesafe.sbt.packager
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import ohnosequences.sbt.SbtS3Resolver
import ohnosequences.sbt.SbtS3Resolver._
import play.sbt.routes.RoutesKeys
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
    settings = projectSettings ++ nativePackageSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy,
        Dependency.metrics
      )
    )
  )
    .aggregate(api, jobs)
    .enablePlugins(PlayScala).disablePlugins(PlayLayoutPlugin)
    .enablePlugins(UniversalDeployPlugin)

  lazy val api = Project(
    id = "api",
    base = file("api"),
    dependencies = Seq(jobs % "compile->compile;test->test"),
    settings = projectSettings ++ Seq(
      RoutesKeys.routesImport ++= Seq("dcos.metronome.api.Binders._"),
      libraryDependencies ++= Seq(
        Dependency.playJson,
        Dependency.playWS,
        Dependency.marathonPlugin,
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy,
        Dependency.yaml,
        Dependency.cron4J,
        Dependency.metrics,
        Dependency.jsonValidate,
        Dependency.Test.scalatest,
        Dependency.Test.scalatestPlay
      )
    )
  ).enablePlugins(PlayScala).disablePlugins(PlayLayoutPlugin)

  lazy val jobs = Project(
    id = "jobs",
    base = file("jobs"),
    settings = projectSettings ++ PB.protobufSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.asyncAwait,
        Dependency.playJson,
        Dependency.marathon,
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy,
        Dependency.cron4J,
        Dependency.akka,
        Dependency.metrics,
        Dependency.Test.akkaTestKit,
        Dependency.Test.mockito
      )
    )
  )

  lazy val projectSettings = baseSettings ++ formatSettings ++ publishSettings

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
      "Spray Maven Repository" at "http://repo.spray.io/",
      "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
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

  lazy val publishSettings = S3Resolver.defaults ++ Seq(
    publishTo := Some(s3resolver.value(
      "Mesosphere Public Repo (S3)",
      s3("downloads.mesosphere.io/maven")
    )),
    SbtS3Resolver.s3credentials := new EnvironmentVariableCredentialsProvider() | new InstanceProfileCredentialsProvider()
  )

  lazy val nativePackageSettings = Seq(
    packager.Keys.bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "bin" / "extra.sh")
  )

  object Dependency {
    object V {
      // Test deps versions
      val AsyncAwait = "0.9.6-RC2"
      val ScalaTest = "2.1.7"
      val MacWire = "2.2.2"
      val Marathon = "1.2.0-RC1"
      val Play = "2.5.3"
      val Cron4J = "2.2.5"
      val WixAccord = "0.5"
      val Akka = "2.3.15"
      val Mockito = "2.0.54-beta"
      val Metrics = "3.5.4_a2.3"
      val JsonValidate = "0.7.0"
    }

    val asyncAwait = "org.scala-lang.modules" %% "scala-async" % V.AsyncAwait
    val playJson = "com.typesafe.play" %% "play-json" % V.Play
    val playWS = "com.typesafe.play" %% "play-ws" % V.Play
    val yaml = "net.jcazevedo" %% "moultingyaml" % "0.2"
    val macWireMacros = "com.softwaremill.macwire" %% "macros" % V.MacWire % "provided"
    val macWireUtil = "com.softwaremill.macwire" %% "util" % V.MacWire
    val macWireProxy = "com.softwaremill.macwire" %% "proxy" % V.MacWire
    val marathon = "mesosphere.marathon" %% "marathon" % V.Marathon exclude("com.typesafe.play", "*") exclude("mesosphere.marathon", "ui") exclude("mesosphere", "chaos") exclude("org.apache.hadoop", "hadoop-hdfs") exclude("org.apache.hadoop", "hadoop-common") exclude("org.eclipse.jetty", "*")
    val marathonPlugin = "mesosphere.marathon" %% "plugin-interface" % V.Marathon
    val cron4J = "it.sauronsoftware.cron4j" % "cron4j" % V.Cron4J
    val wixAccord = "com.wix" %% "accord-core" % V.WixAccord
    val akka = "com.typesafe.akka" %%  "akka-actor" % V.Akka
    val metrics = "nl.grons" %% "metrics-scala" % V.Metrics
    val jsonValidate = "com.eclipsesource" %% "play-json-schema-validator" % V.JsonValidate

    object Test {
      val scalatest = "org.scalatest" %% "scalatest" % V.ScalaTest % "test"
      val scalatestPlay = "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % "test"
      val akkaTestKit = "com.typesafe.akka" %%  "akka-testkit" % V.Akka % "test"
      val mockito = "org.mockito" % "mockito-core" % V.Mockito % "test"
    }
  }
}

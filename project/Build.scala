import com.amazonaws.auth.{EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider}
import com.typesafe.sbt.packager
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import ohnosequences.sbt.SbtS3Resolver
import ohnosequences.sbt.SbtS3Resolver._
import play.sbt.routes.RoutesKeys
import play.sbt.{PlayLayoutPlugin, PlayScala}
import sbt.Keys._
import sbt.{ExclusionRule, _}
import sbtprotobuf.{ProtobufPlugin => PB}
import sbtprotobuf.ProtobufPlugin.Keys.ProtobufConfig
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbt.SbtAspectj._

import scalariform.formatter.preferences._


object Build extends sbt.Build {

  val pbSettings = PB.projectSettings ++ Seq(
    (version in ProtobufConfig) := "3.3.0"
  )
  lazy val metronome = Project(
    id = "metronome",
    base = file("."),
    dependencies = Seq(api, jobs),
    settings = projectSettings ++ nativePackageSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy
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
        Dependency.cronUtils,
        Dependency.threeten,
        Dependency.jsonValidate,
        Dependency.iteratees,
        Dependency.playAhcWS,
        Dependency.Test.scalatest,
        Dependency.Test.scalatestPlay
      ).map(
        _.excludeAll(excludeSlf4jLog4j12)
          .excludeAll(excludeLog4j)
          .excludeAll(excludeJCL)
          .excludeAll(excludeAkkaHttpExperimental)
          .excludeAll(excludeKamonAkka)
          .excludeAll(excludeKamonAutoweave)
          .excludeAll(excludeKamonScala)
      )
    )
  ).enablePlugins(PlayScala).disablePlugins(PlayLayoutPlugin)

  val excludeSlf4jLog4j12 = ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
  val excludeKamonAkka = ExclusionRule(organization = "io.kamon", name = "kamon-akka-2.4")
  val excludeKamonAutoweave = ExclusionRule(organization = "io.kamon", name = "kamon-autoweave_2.11")
  val excludeKamonScala = ExclusionRule(organization = "io.kamon", name = "kamon-scala_2.11")
  val excludeLog4j = ExclusionRule(organization = "log4j", name = "log4j")
  val excludeJCL = ExclusionRule(organization = "commons-logging", name = "commons-logging")
  val excludeAkkaHttpExperimental = ExclusionRule(organization = "com.typesafe.akka", name = "akka-http-experimental_2.11")

  lazy val jobs = Project(
    id = "jobs",
    base = file("jobs"),
    settings = projectSettings ++ pbSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependency.asyncAwait,
        Dependency.playJson,
        Dependency.marathon,
        Dependency.marathonPlugin,
        Dependency.macWireMacros,
        Dependency.macWireUtil,
        Dependency.macWireProxy,
        Dependency.threeten,
        Dependency.cronUtils,
        Dependency.akka,
        Dependency.twitterCommons,
        Dependency.twitterZk,
        Dependency.Test.scalatest,
        Dependency.Test.akkaTestKit,
        Dependency.Test.mockito
      ).map(
        _.excludeAll(excludeSlf4jLog4j12)
          .excludeAll(excludeLog4j)
          .excludeAll(excludeJCL)
          .excludeAll(excludeAkkaHttpExperimental)
          .excludeAll(excludeKamonAkka)
          .excludeAll(excludeKamonAutoweave)
          .excludeAll(excludeKamonScala)
      )
    )
  )

  lazy val projectSettings = baseSettings ++ formatSettings ++ publishSettings

  lazy val baseSettings = Seq(
    organization := "dcos",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq(scalaVersion.value),
    scalacOptions in (Compile, doc) ++= Seq(
      "-encoding", "UTF-8",
      "-target:jvm-1.8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      "-no-link-warnings" // Suppresses problems with Scaladoc @throws links
    ),
    javacOptions in Compile ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
    resolvers ++= Seq(
      "Mesosphere Public Repo" at "http://downloads.mesosphere.io/maven",
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
      "Spray Maven Repository" at "http://repo.spray.io/",
      "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
    ),
    fork in Test := true
  ) ++ aspectjSettings

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
      val AsyncAwait = "0.9.7"
      val ScalaTest = "3.0.5"
      val MacWire = "2.2.5"
      val Marathon = "1.5.6-19-g2d4e150"
      val MarathonPluginInterface = "1.5.6-19-g2d4e150"
      val Play = "2.6.7"
      val CronUtils = "6.0.4"
      val Threeten = "1.3.3"
      val WixAccord = "0.7.1"
      val Akka = "2.4.20"
      val Mockito = "2.0.54-beta"
      val JsonValidate = "0.9.4"
      val TwitterCommons = "0.0.76"
      val TwitterZk = "6.40.0"
    }

    val asyncAwait = "org.scala-lang.modules" %% "scala-async" % V.AsyncAwait
    val iteratees = "com.typesafe.play" %% "play-iteratees" % "2.6.1"
    val playAhcWS   = "com.typesafe.play" %% "play-ahc-ws" % V.Play
    val playJson = "com.typesafe.play" %% "play-json" % V.Play
    val playWS = "com.typesafe.play" %% "play-ws" % V.Play
    val yaml = "net.jcazevedo" %% "moultingyaml" % "0.2"
    val macWireMacros = "com.softwaremill.macwire" %% "macros" % V.MacWire % "provided"
    val macWireUtil = "com.softwaremill.macwire" %% "util" % V.MacWire
    val macWireProxy = "com.softwaremill.macwire" %% "proxy" % V.MacWire
    val marathon = "mesosphere.marathon" %% "marathon" % V.Marathon exclude("com.typesafe.play", "*") exclude("mesosphere.marathon", "ui") exclude("mesosphere", "chaos") exclude("org.apache.hadoop", "hadoop-hdfs") exclude("org.apache.hadoop", "hadoop-common") exclude("org.eclipse.jetty", "*") exclude("mesosphere.marathon", "plugin-interface_2.11")
    val marathonPlugin = "mesosphere.marathon" %% "plugin-interface" % V.MarathonPluginInterface
    val cronUtils = "com.cronutils" % "cron-utils" % V.CronUtils exclude("org.threeten", "threetenbp")
    val threeten = "org.threeten" % "threetenbp" % V.Threeten
    val wixAccord = "com.wix" %% "accord-core" % V.WixAccord
    val akka = "com.typesafe.akka" %%  "akka-actor" % V.Akka
    val jsonValidate = "com.eclipsesource" %% "play-json-schema-validator" % V.JsonValidate
    val twitterCommons = "com.twitter.common.zookeeper" % "candidate" % V.TwitterCommons
    val twitterZk = "com.twitter" %% "util-zk" % V.TwitterZk

    object Test {
      val scalatest = "org.scalatest" %% "scalatest" % V.ScalaTest % "test"
      val scalatestPlay = "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"
      val akkaTestKit = "com.typesafe.akka" %%  "akka-testkit" % V.Akka % "test"
      val mockito = "org.mockito" % "mockito-core" % V.Mockito % "test"
    }
  }
}

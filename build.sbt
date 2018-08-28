import com.amazonaws.auth.{
  AWSCredentialsProviderChain,
  EnvironmentVariableCredentialsProvider,
  InstanceProfileCredentialsProvider
}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtScalariform.autoImport._
import com.typesafe.sbt.packager
import play.sbt.routes.RoutesKeys
import sbtprotobuf.{ProtobufPlugin => PB}
import scalariform.formatter.preferences._

lazy val projectSettings = baseSettings ++ formatSettings ++ publishSettings

lazy val baseSettings = Seq(
  organization := "dcos",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq(scalaVersion.value),
  scalacOptions in (Compile, doc) ++= Seq(
    "-encoding",
    "UTF-8",
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
  javacOptions in Compile ++= Seq(
    "-encoding",
    "UTF-8",
    "-source",
    "1.8",
    "-target",
    "1.8",
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  ),
  resolvers ++= Seq(
    Resolver.JCenterRepository,
    "Mesosphere Public Repo" at "http://downloads.mesosphere.io/maven",
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    "Spray Maven Repository" at "http://repo.spray.io/",
    "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
  ),
  fork in Test := true
)

val excludeSlf4jLog4j12 =
  ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
val excludeLog4j = ExclusionRule(organization = "log4j", name = "log4j")
val excludeJCL =
  ExclusionRule(organization = "commons-logging", name = "commons-logging")
val excludeAkkaHttpExperimental = ExclusionRule(
  organization = "com.typesafe.akka",
  name = "akka-http-experimental_2.12"
)

lazy val formatSettings = Seq(
  scalariformAutoformat := true,
  ScalariformKeys.preferences := FormattingPreferences()
    .setPreference(AlignParameters, true)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(SpacesAroundMultiImports, true)
    .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)
)

lazy val publishSettings = Seq(
  publishTo := Some(
    s3resolver
      .value("Mesosphere Public Repo (S3)", s3("downloads.mesosphere.io/maven"))
  ),
  s3credentials := DefaultAWSCredentialsProviderChain.getInstance(),
  s3region :=  com.amazonaws.services.s3.model.Region.US_Standard
)

lazy val nativePackageSettings = Seq(
  packager.Keys.bashScriptExtraDefines ++= IO
    .readLines(baseDirectory.value / "bin" / "extra.sh")
)

val pbSettings = PB.projectSettings ++ Seq(
  (version in ProtobufConfig) := "3.3.0"
)

lazy val metronome = (project in file("."))
  .dependsOn(api, jobs)
  .aggregate(api, jobs)
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .enablePlugins(UniversalDeployPlugin)
  .settings(projectSettings)
  .settings(nativePackageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.macWireMacros,
      Dependencies.macWireUtil,
      Dependencies.macWireProxy
    )
  )

val silencerVersion = "1.1"
addCompilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion)

lazy val api = (project in file("api"))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(jobs % "compile->compile;test->test")
  .settings(projectSettings)
  .settings(
    RoutesKeys.routesImport ++= Seq("dcos.metronome.api.Binders._"),
    libraryDependencies ++= Seq(
      Dependencies.playJson,
      Dependencies.playWS,
      Dependencies.marathonPlugin,
      Dependencies.macWireMacros,
      Dependencies.macWireUtil,
      Dependencies.macWireProxy,
      Dependencies.yaml,
      Dependencies.cronUtils,
      Dependencies.threeten,
      Dependencies.jsonValidate,
      Dependencies.iteratees,
      Dependencies.playAhcWS,
      Dependencies.Test.scalatest,
      Dependencies.Test.scalaCheck,
      Dependencies.Test.scalatestPlay,
      "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided
    ).map(
      _.excludeAll(excludeSlf4jLog4j12)
        .excludeAll(excludeLog4j)
        .excludeAll(excludeJCL)
        .excludeAll(excludeAkkaHttpExperimental)
    )
  )

lazy val jobs = (project in file("jobs"))
  .settings(projectSettings)
  .settings(pbSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.asyncAwait,
      Dependencies.playJson,
      Dependencies.marathon,
      Dependencies.marathonPlugin,
      Dependencies.macWireMacros,
      Dependencies.macWireUtil,
      Dependencies.macWireProxy,
      Dependencies.threeten,
      Dependencies.cronUtils,
      Dependencies.akka,
      Dependencies.twitterCommons,
      Dependencies.twitterZk,
      Dependencies.Test.scalatest,
      Dependencies.Test.akkaTestKit,
      Dependencies.Test.mockito,
      Dependencies.Test.scalatest,
      Dependencies.Test.scalaCheck
    ).map(
      _.excludeAll(excludeSlf4jLog4j12)
        .excludeAll(excludeLog4j)
        .excludeAll(excludeJCL)
        .excludeAll(excludeAkkaHttpExperimental)
    )
  )

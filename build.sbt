
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.model.Region
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtScalariform.autoImport._
import com.typesafe.sbt.packager
import play.sbt.routes.RoutesKeys
import sbtprotobuf.{ProtobufPlugin => PB}
import scalariform.formatter.preferences._

lazy val projectSettings = baseSettings ++ formatSettings ++ publishSettings

lazy val writeVersion = taskKey[Unit]("Output version to target folder")

writeVersion := {
  val s = streams.value
  val file = (target in Compile).value / "version.txt"
  IO.write(file, version.value)
  s.log.info(s"Wrote version ${version.value} to ${file}")
}

lazy val packagingSettings = Seq(
  (packageName in Universal) := {
    import sys.process._
    val shortCommit = ("./version commit" !!).trim
    s"${packageName.value}-${version.value}-$shortCommit"
  })

lazy val baseSettings = Seq(
  version := {
    import sys.process._
    ("./version" !!).trim
  },
  organization := "dcos",
  scalaVersion := "2.12.7",
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
  s3region :=  Region.US_Standard
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
  .enablePlugins(JavaServerAppPackaging)
  .disablePlugins(PlayLayoutPlugin)
  .enablePlugins(UniversalDeployPlugin)
  .settings(projectSettings)
  .settings(nativePackageSettings)
  .settings(packagingSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.macWireMacros,
      Dependencies.macWireUtil,
      Dependencies.macWireProxy,
      Dependencies.Test.scalatest,
      Dependencies.Test.usiTestUtils
    )
      .map(
        _.excludeAll(excludeSlf4jLog4j12)
          .excludeAll(excludeLog4j)
          .excludeAll(excludeJCL)
          .excludeAll(excludeAkkaHttpExperimental)
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
  .settings(version := {
    import sys.process._
    ("./version" !!).trim
  }, projectSettings)
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
      Dependencies.cronUtils,
      Dependencies.akka,
      Dependencies.twitterCommons,
      Dependencies.twitterZk,
      Dependencies.caffeine,
      Dependencies.Test.scalatest,
      Dependencies.Test.akkaTestKit,
      Dependencies.Test.mockito,
      Dependencies.Test.scalatest,
      Dependencies.Test.scalaCheck,
    ).map(
      _.excludeAll(excludeSlf4jLog4j12)
        .excludeAll(excludeLog4j)
        .excludeAll(excludeJCL)
        .excludeAll(excludeAkkaHttpExperimental)
    )
  )

lazy val integrationTestSettings = Seq(
  testListeners := Nil, // TODO(MARATHON-8215): Remove this line

  fork in Test := true,
  testOptions in Test := Seq(
    Tests.Argument(
      "-u", "target/test-reports", // TODO(MARATHON-8215): Remove this line
      "-o", "-eDFG",
      "-y", "org.scalatest.WordSpec")),
  parallelExecution in Test := true,
  testForkedParallel in Test := true,
  concurrentRestrictions in Test := Seq(Tags.limitAll(math.max(1, java.lang.Runtime.getRuntime.availableProcessors() / 2))),
  javaOptions in (Test, test) ++= Seq(
    "-Dakka.actor.default-dispatcher.fork-join-executor.parallelism-min=2",
    "-Dakka.actor.default-dispatcher.fork-join-executor.factor=1",
    "-Dakka.actor.default-dispatcher.fork-join-executor.parallelism-max=4",
    "-Dscala.concurrent.context.minThreads=2",
    "-Dscala.concurrent.context.maxThreads=32"
  ),
  concurrentRestrictions in Test := Seq(Tags.limitAll(math.max(1, java.lang.Runtime.getRuntime.availableProcessors() / 2)))
)

lazy val integration = (project in file("./tests/integration"))
  .settings(integrationTestSettings : _*)
  .settings(projectSettings: _*)
  .settings(
    cleanFiles += baseDirectory { base => base / "sandboxes" }.value
  )
  .dependsOn(metronome % "test->test")
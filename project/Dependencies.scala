import sbt._

object Dependencies {
  object V {
    val AsyncAwait = "0.9.7"
    val ScalaTest = "3.0.5"
    val ScalaCheck = "1.14.0"
    val MacWire = "2.3.1"
    val Marathon = "1.9.94"
    val MarathonPluginInterface = "1.7.202"
    val Play = "2.6.18"
    val PlayJson = "2.6.10"
    val ScalaTestPlusPlay = "3.1.2"
    val PlayIteratees = "2.6.1"
    val CronUtils = "9.0.0"
    val WixAccord = "0.7.1"
    val Akka = "2.5.15"
    val Mockito = "2.21.0"
    val JsonValidate = "0.9.4"
    val TwitterCommons = "0.0.76"
    val TwitterZk = "18.8.0"
    val MoultingYaml = "0.4.0"
    val Caffeine = "2.6.2"
    val UsiTestUtils = "0.1.4"
  }

  val asyncAwait = "org.scala-lang.modules" %% "scala-async" % V.AsyncAwait
  val iteratees = "com.typesafe.play" %% "play-iteratees" % V.PlayIteratees
  val playWS = "com.typesafe.play" %% "play-ws" % V.Play
  val playAhcWS   = "com.typesafe.play" %% "play-ahc-ws" % V.Play
  val playJson = "com.typesafe.play" %% "play-json" % V.PlayJson
  val yaml = "net.jcazevedo" %% "moultingyaml" % V.MoultingYaml
  val macWireMacros = "com.softwaremill.macwire" %% "macros" % V.MacWire % "provided"
  val macWireUtil = "com.softwaremill.macwire" %% "util" % V.MacWire
  val macWireProxy = "com.softwaremill.macwire" %% "proxy" % V.MacWire
  val marathon = "mesosphere.marathon" %% "marathon" % V.Marathon exclude("com.typesafe.play", "*") exclude("mesosphere.marathon", "ui") exclude("mesosphere", "chaos") exclude("org.apache.hadoop", "hadoop-hdfs") exclude("org.apache.hadoop", "hadoop-common") exclude("mesosphere.marathon", "plugin-interface_2.12")
  val marathonPlugin = "mesosphere.marathon" %% "plugin-interface" % V.MarathonPluginInterface
  val cronUtils = "com.cronutils" % "cron-utils" % V.CronUtils exclude("org.threeten", "threetenbp")
  val wixAccord = "com.wix" %% "accord-core" % V.WixAccord
  val akka = "com.typesafe.akka" %%  "akka-actor" % V.Akka
  val jsonValidate = "com.eclipsesource" %% "play-json-schema-validator" % V.JsonValidate
  val twitterCommons = "com.twitter.common.zookeeper" % "candidate" % V.TwitterCommons
  val twitterZk = "com.twitter" %% "util-zk" % V.TwitterZk exclude("com.github.ben-manes.caffeine", "caffeine")
  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % V.Caffeine // we need to override caffeine version because of dependency in dcos plugins

  val scallop = "org.rogach" %% "scallop" % "3.1.2"
  val uuidGenerator = "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.4"
  val jGraphT = "org.javabits.jgrapht" % "jgrapht-core" % "0.9.3"
  val java8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
  val mesos = "org.apache.mesos" % "mesos" % "1.9.0"

  object Test {
    val scalatest = "org.scalatest" %% "scalatest" % V.ScalaTest % "test"
    val scalaCheck = "org.scalacheck" %% "scalacheck" % V.ScalaCheck % "test"
    val scalatestPlay = "org.scalatestplus.play" %% "scalatestplus-play" % V.ScalaTestPlusPlay % "test"
    val akkaTestKit = "com.typesafe.akka" %%  "akka-testkit" % V.Akka % "test"
    val mockito = "org.mockito" % "mockito-core" % V.Mockito % "test"

    val usiTestUtils = "com.mesosphere.usi" % "test-utils" % V.UsiTestUtils % "test"

  }


  object Curator {
    /**
      * According to Curator's Zookeeper Compatibility Docs [http://curator.apache.org/zk-compatibility.html], 4.0.0
      * is the recommended version to use with Zookeeper 3.4.x. You do need to exclude the 3.5.x dependency and specify
      * your 3.4.x dependency.
      */
    val Version = "4.0.1"

    val TestVersion = "2.12.0"

    val excludeZk35 = ExclusionRule(organization = "org.apache.zookeeper", name = "zookeeper")

    val curator = Seq(
      "org.apache.curator" % "curator-recipes" % Version % "compile",
      "org.apache.curator" % "curator-client" % Version % "compile",
      "org.apache.curator" % "curator-framework" % Version % "compile",
      "org.apache.curator" % "curator-x-async" % Version % "compile",
      "org.apache.curator" % "curator-test" % TestVersion % "test").map(_.excludeAll(excludeZk35))

    val zk = Seq("org.apache.zookeeper" % "zookeeper" % "3.4.11")
    val all = curator ++ zk
  }

  object DropwizardMetrics {
    val Version = "4.0.2"

    val core = "io.dropwizard.metrics" % "metrics-core" % Version % "compile"
    val jersey = "io.dropwizard.metrics" % "metrics-jersey2" % Version % "compile"
    val jetty = "io.dropwizard.metrics" % "metrics-jetty9" % Version % "compile"
    val jvm = "io.dropwizard.metrics" % "metrics-jvm" % Version % "compile"
    val servlets = "io.dropwizard.metrics" % "metrics-servlets" % Version % "compile"
    val rollingMetrics = "com.github.vladimir-bukhtoyarov" % "rolling-metrics" % "2.0.4" % "compile"
    val hdrHistogram = "org.hdrhistogram" % "HdrHistogram" % "2.1.10" % "compile"

    val all = Seq(core, jersey, jetty, jvm, servlets, rollingMetrics, hdrHistogram)
  }

}

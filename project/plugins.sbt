resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.jcenterRepo

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.7")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

resolvers += "Era7 maven releases" at "http://releases.era7.com.s3.amazonaws.com"

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.19.0")

resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.2")

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.3")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC13")

addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0") // because of Kamon

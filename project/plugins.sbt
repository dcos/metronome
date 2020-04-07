resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "Era7 maven releases" at "https://releases.era7.com.s3.amazonaws.com"
resolvers += Classpaths.sbtPluginReleases
resolvers += Resolver.jcenterRepo

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.18")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.19.0")

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.3")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.11")

libraryDependencies += "com.github.os72" % "protoc-jar" % "3.8.0"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.9")

libraryDependencies += "com.github.os72" % "protoc-jar" % "3.8.0"

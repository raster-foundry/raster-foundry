resolvers ++= Seq(
  Classpaths.sbtPluginReleases,
  Opts.resolver.sonatypeReleases
)

resolvers += Classpaths.typesafeResolver

resolvers += Resolver.bintrayIvyRepo("kamon-io", "sbt-plugins")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.10")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.4")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.0")

addSbtPlugin("io.kamon" % "sbt-aspectj-runner" % "1.0.1")

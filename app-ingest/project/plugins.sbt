addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  Classpaths.sbtPluginReleases,
  Opts.resolver.sonatypeReleases
)

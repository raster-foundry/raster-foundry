val Http4sVersion = "0.20.0-M3"
val GeotrellisServerVersion = "0.0.13"
val Specs2Version = "4.1.0"
val ScalatestVersion = "3.0.5"

lazy val root = (project in file("."))
  .settings(
    organization := "com.rasterfoundry",
    name := "backsplash",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.12",
    fork in run := true,
    externalResolvers := Seq(
      DefaultMavenRepository,
      Resolver.sonatypeRepo("snapshots"),
      Resolver.bintrayRepo("azavea", "maven"),
      Resolver.bintrayRepo("azavea", "geotrellis"),
      "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
      "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
      Classpaths.sbtPluginReleases,
      Opts.resolver.sonatypeReleases,
      Resolver.file("local", file(Path.userHome.absolutePath + "/.ivy2/local"))(
        Resolver.ivyStylePatterns
      ) // important to pull deps from the local repo
    ),
    libraryDependencies ++= Seq(
      "org.http4s"       %% "http4s-blaze-server"    % Http4sVersion,
      "org.http4s"       %% "http4s-circe"           % Http4sVersion,
      "org.http4s"       %% "http4s-dsl"             % Http4sVersion,
      "org.scalatest"    %% "scalatest"              % ScalatestVersion % Test,
      "com.azavea"       %% "geotrellis-server-core" % GeotrellisServerVersion,
      "org.scalacheck"   %% "scalacheck"             % "1.14.0",
      "org.apache.spark" %% "spark-core"             % "2.4.0" % Provided
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
    addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )


name := "rf-server"

scalaVersion := Version.scala
scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

scapegoatVersion := Version.scapegoat

fork in run := true

connectInput in run := true
cancelable in Global := true

assemblyJarName in assembly := "rf-server.jar"

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

test in assembly := {}

resolvers ++= Seq(
  "Open Source Geospatial Foundation Repo" at "http://download.osgeo.org/webdav/geotools/",
  "sonatypeSnapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  Resolver.bintrayRepo("azavea", "geotrellis"),
  Resolver.bintrayRepo("azavea", "maven")
)

libraryDependencies ++= Seq(
  Dependencies.akka,
  Dependencies.akkastream,
  Dependencies.akkahttp,
  Dependencies.akkajson,
  Dependencies.akkatestkit,
  Dependencies.scalatest,
  Dependencies.slick,
  Dependencies.hikariCP,
  Dependencies.postgres,
  Dependencies.slf4j
)



name := "rf-server"

addCommandAlias("mg", "migrations/run")
addCommandAlias("mgm", "migration_manager/run")

lazy val commonSettings = Seq(
  organization := "com.azavea",
  version := Version.rasterFoundry,
  scapegoatVersion := Version.scapegoat,
  scapegoatIgnoredFiles := Seq(".*/generated_code/.*"),
  scalaVersion := Version.scala,
  scalacOptions := Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:existentials",
    "-language:experimental.macros",
    "-feature"
  ),
  resolvers += Resolver.sonatypeRepo("snapshots")
)

lazy val appSettings = commonSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true,
  assemblyJarName in assembly := "rf-server.jar",
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
    case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
    case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  },
  resolvers += "Open Source Geospatial Foundation Repo" at "http://download.osgeo.org/webdav/geotools/",
  resolvers += Resolver.bintrayRepo("azavea", "geotrellis"),
  resolvers += Resolver.bintrayRepo("azavea", "maven"),
  resolvers += Resolver.bintrayRepo("lonelyplanet", "maven"),
  test in assembly := {}
)

lazy val loggingDependencies = List(
  Dependencies.slf4j
)

lazy val slickDependencies = List(
  Dependencies.slick
)

lazy val dbDependencies = List(
  Dependencies.hikariCP,
  Dependencies.postgres
)

lazy val forkliftDependencies = List(
  Dependencies.scalaforklift
)

lazy val migrationsDependencies =
  dbDependencies ++ forkliftDependencies ++ loggingDependencies

lazy val appDependencies = dbDependencies ++ loggingDependencies ++ Seq(
  Dependencies.akka,
  Dependencies.akkahttp,
  Dependencies.akkajson,
  Dependencies.akkastream,
  Dependencies.akkatestkit,
  Dependencies.scalatest,
  Dependencies.authCommon,
  Dependencies.authAkka,
  Dependencies.akkaHttpExtensions
)

lazy val migrationManagerDependencies = dbDependencies ++ forkliftDependencies

lazy val root = Project("root", file(".")).aggregate(
  app, migrations, migrationManager, generatedCode).settings(
  commonSettings:_*)

lazy val app = Project("app",
  file("app")).dependsOn(generatedCode).settings(
  appSettings:_*).settings {
  libraryDependencies ++= appDependencies
}

lazy val migrationManager = Project("migration_manager",
  file("migration_manager")).settings(
  commonSettings:_*).settings {
  libraryDependencies ++= migrationManagerDependencies
}

lazy val migrations = Project("migrations",
  file("migrations")).dependsOn(
  generatedCode, migrationManager).settings(
  commonSettings:_*).settings {
  libraryDependencies ++= migrationsDependencies
}

lazy val generatedCode = Project("generate_code",
  file("generated_code")).settings(commonSettings:_*).settings {
  libraryDependencies ++= slickDependencies
}

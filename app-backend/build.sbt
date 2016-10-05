name := "rf-backend"

addCommandAlias("mg", "migrations/run")
addCommandAlias("mgm", "migration_manager/run")

lazy val commonSettings = Seq(
  organization := "com.azavea",
  version := Version.rasterFoundry,
  scapegoatVersion := Version.scapegoat,
  scapegoatIgnoredFiles := Seq(".*/datamodel/.*"),
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
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  resolvers += Resolver.sonatypeRepo("snapshots")
)


lazy val appSettings = commonSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true,
  assemblyJarName in assembly := "rf-backend.jar",
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
  "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.10",
  "ch.qos.logback" %  "logback-classic" % "1.1.7"
)

lazy val slickDependencies = List(
  Dependencies.slick,
  Dependencies.slickPG,
  Dependencies.slickPGSpray,
  Dependencies.geotrellisSlick
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

lazy val appDependencies = dbDependencies ++ migrationsDependencies ++ Seq(
  Dependencies.akka,
  Dependencies.akkahttp,
  Dependencies.akkaHttpCors,
  Dependencies.akkajson,
  Dependencies.akkastream,
  Dependencies.akkatestkit,
  Dependencies.scalatest,
  Dependencies.authCommon,
  Dependencies.authAkka,
  Dependencies.akkaHttpExtensions,
  Dependencies.ammoniteOps,
  Dependencies.geotrellisSlick
)

lazy val migrationManagerDependencies = dbDependencies ++ forkliftDependencies

lazy val root = Project("root", file("."))
  .aggregate(app, migrations, migrationManager, datamodel, ingest)
  .settings(commonSettings:_*)

lazy val app = Project("app", file("app"))
  .dependsOn(datamodel)
  .settings(appSettings:_*)
  .settings({
    libraryDependencies ++= appDependencies
  })

lazy val migrationManager = Project("migration_manager", file("migration_manager"))
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= migrationManagerDependencies
  })

lazy val migrations = Project("migrations", file("migrations"))
  .dependsOn(datamodel, migrationManager)
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= migrationsDependencies
  })

lazy val datamodel = Project("datamodel", file("datamodel"))
  .settings(commonSettings:_*).settings({
    libraryDependencies ++= slickDependencies
  })

lazy val ingest = Project("ingest", file("ingest"))
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= slickDependencies ++ Seq(
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisVector,
      Dependencies.geotrellisSparkEtl,
      Dependencies.geotrellisS3,
      Dependencies.akkajson,
      Dependencies.spark
    )
  })

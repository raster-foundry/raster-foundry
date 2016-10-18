name := "rf-backend"

addCommandAlias("mg", "migrations/run")

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
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.bintrayRepo("lonelyplanet", "maven"),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
)

lazy val appSettings = commonSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true
)
lazy val loggingDependencies = List(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
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
  Dependencies.akkaSlf4j,
  Dependencies.scalatest,
  Dependencies.authCommon,
  Dependencies.authAkka,
  Dependencies.akkaHttpExtensions,
  Dependencies.ammoniteOps,
  Dependencies.geotrellisSlick
)

lazy val root = Project("root", file("."))
  .aggregate(app, migrations, datamodel, database)
  .settings(commonSettings:_*)

lazy val app = Project("app", file("app"))
  .dependsOn(database, datamodel)
  .settings(appSettings:_*)
  .settings({
    libraryDependencies ++= appDependencies
  })

lazy val migrations = Project("migrations", file("migrations"))
  .dependsOn(datamodel)
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= migrationsDependencies
  })


lazy val datamodel = Project("datamodel", file("datamodel"))
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.geotrellisSlick % "provided",
      Dependencies.akkajson
    )
  })

lazy val database = Project("database", file("database"))
  .dependsOn(datamodel)
  .settings(commonSettings:_*)
  .settings({
   libraryDependencies ++= slickDependencies ++ dbDependencies ++ loggingDependencies ++ Seq(Dependencies.akkaHttpExtensions)
  })

lazy val ingest = Project("ingest", file("ingest"))
  .dependsOn(datamodel)
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

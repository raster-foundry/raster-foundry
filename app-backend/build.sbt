name := "rf-backend"

addCommandAlias("mg", "migrations/run")

scalaVersion := Version.scala

lazy val commonSettings = Seq(
  organization := "com.azavea",
  version := Version.rasterFoundry,
  cancelable in Global := true,
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

lazy val apiSettings = commonSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true,
  assemblyJarName in assembly := "rf-server.jar",
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  },
  resolvers += "Open Source Geospatial Foundation Repo" at "http://download.osgeo.org/webdav/geotools/",
  resolvers += "LocationTech GeoTrellis Releases" at "https://repo.locationtech.org/content/repositories/geotrellis-releases",
  resolvers += Resolver.bintrayRepo("azavea", "maven"),
  resolvers += Resolver.bintrayRepo("lonelyplanet", "maven"),
  test in assembly := {}
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
    .exclude("postgresql", "postgresql")
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

lazy val testDependencies = List(
    Dependencies.scalatest,
    Dependencies.geotrellisRasterTestkit,
    Dependencies.akkatestkit
)

lazy val apiDependencies = dbDependencies ++ migrationsDependencies ++
    testDependencies ++ Seq(
  Dependencies.akka,
  Dependencies.akkahttp,
  Dependencies.akkaHttpCors,
  Dependencies.akkaCirceJson,
  Dependencies.akkastream,
  Dependencies.akkaSlf4j,
  Dependencies.akkaHttpExtensions,
  Dependencies.commonsIO,
  Dependencies.ammoniteOps,
  Dependencies.geotrellisSlick,
  Dependencies.geotrellisS3,
  Dependencies.caffeine,
  Dependencies.scaffeine,
  Dependencies.findbugAnnotations
)

lazy val root = Project("root", file("."))
  .aggregate(api, migrations, datamodel, database, ingest)
  .settings(commonSettings:_*)

lazy val api = Project("api", file("api"))
  .dependsOn(database, datamodel, common)
  .settings(apiSettings:_*)
  .settings(resolvers += Resolver.bintrayRepo("hseeberger", "maven"))
  .settings({
    libraryDependencies ++= apiDependencies
  })

lazy val common = Project("common", file("common"))
  .dependsOn(database, datamodel)
  .settings(apiSettings:_*)
  .settings({libraryDependencies ++= testDependencies ++ Seq(
    Dependencies.jwtCore,
    Dependencies.json4s,
    Dependencies.jwtJson,
    Dependencies.akka,
    Dependencies.akkahttp,
    Dependencies.commonsIO,
    Dependencies.caffeine,
    Dependencies.scaffeine,
    Dependencies.elasticacheClient,
    Dependencies.geotrellisS3,
    Dependencies.findbugAnnotations,
    Dependencies.chill,
    Dependencies.cats
  )})

lazy val migrations = Project("migrations", file("migrations"))
  .dependsOn(datamodel)
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= migrationsDependencies
  })

lazy val datamodel = Project("datamodel", file("datamodel"))
  .dependsOn(tool)
  .settings(commonSettings:_*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.geotrellisSlick % "provided",
      Dependencies.geotrellisRaster,
      Dependencies.circeCore,
      Dependencies.akka,
      Dependencies.akkahttp
    )
  })

lazy val database = Project("database", file("database"))
  .dependsOn(datamodel)
  .settings(commonSettings:_*)
  .settings({
     libraryDependencies ++= slickDependencies ++ dbDependencies ++ loggingDependencies ++ Seq(
       Dependencies.akkaHttpExtensions,
       Dependencies.slickPGCirce
     )
  })

lazy val ingest = Project("ingest", file("ingest"))
  .settings(commonSettings:_*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= testDependencies ++ Seq(
      Dependencies.scalaLogging,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisRaster,
      Dependencies.akkaSprayJson,
      Dependencies.spark,
      Dependencies.scopt
    )
  })

lazy val export = Project("export", file("export"))
  .settings(commonSettings:_*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= testDependencies ++ Seq(
      Dependencies.scalaLogging,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisRaster,
      Dependencies.akkaSprayJson,
      Dependencies.spark,
      Dependencies.scopt
    )
  })

import io.gatling.sbt.GatlingPlugin
lazy val tile = Project("tile", file("tile"))
  .dependsOn(datamodel)
  .dependsOn(database)
  .dependsOn(common)
  .dependsOn(tool)
  .dependsOn(ingest)
  .enablePlugins(GatlingPlugin)
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ testDependencies ++ Seq(
      Dependencies.spark,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.akkaSprayJson,
      Dependencies.circeCore % "it,test",
      Dependencies.circeGeneric % "it,test",
      Dependencies.circeParser % "it,test",
      Dependencies.circeOptics % "it,test",
      Dependencies.scalajHttp % "it,test",
      Dependencies.gatlingApp,
      Dependencies.gatlingTest,
      Dependencies.gatlingHighcharts
    )
  })
  .settings(assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  })
  .settings(assemblyJarName in assembly := "rf-tile-server.jar")

lazy val tool = Project("tool", file("tool"))
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.geotrellisRaster,
      Dependencies.shapeless,
      Dependencies.scalatest,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.circeOptics
    )
  })

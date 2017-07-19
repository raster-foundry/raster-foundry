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
    "-feature",
    "-Ypartial-unification",
    "-Ypatmat-exhaust-depth", "100"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("lonelyplanet", "maven"),
    Resolver.bintrayRepo("kwark", "maven") // Required for Slick 3.1.1.2, see https://github.com/azavea/raster-foundry/pull/1576
  ),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

// Create a new MergeStrategy for aop.xml files
val aopMerge = new sbtassembly.MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._

  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
    val file = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem] = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
    val weaverAttr = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
    val aspects = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

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
    case PathList("META-INF", "aop.xml") => aopMerge
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

lazy val metricsDependencies = List(
  Dependencies.kamonCore,
  Dependencies.kamonStatsd,
  Dependencies.kamonAkkaHttp
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
    testDependencies ++ metricsDependencies ++ Seq(
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
  Dependencies.findbugAnnotations,
  Dependencies.dropbox
)

lazy val root = Project("root", file("."))
  .aggregate(api, common, migrations, datamodel, database, batch, tile, tool, bridge)
  .settings(commonSettings:_*)

lazy val api = Project("api", file("api"))
  .dependsOn(database, datamodel, common % "test->test;compile->compile")
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
    Dependencies.akkaCirceJson,
    Dependencies.commonsIO,
    Dependencies.caffeine,
    Dependencies.scaffeine,
    Dependencies.elasticacheClient,
    Dependencies.geotrellisS3,
    Dependencies.findbugAnnotations,
    Dependencies.ammoniteOps,
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
  .dependsOn(tool, bridge)
  .settings(commonSettings:_*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.geotrellisSlick % "provided",
      Dependencies.geotrellisRaster,
      Dependencies.circeCore,
      Dependencies.circeGenericExtras,
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

lazy val batch = Project("batch", file("batch"))
  .dependsOn(common, datamodel, database, tool, bridge)
  .settings(commonSettings:_*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= testDependencies ++ Seq(
      Dependencies.scalaLogging,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisRaster,
      Dependencies.akka,
      Dependencies.akkahttp,
      Dependencies.akkaHttpCors,
      Dependencies.akkaCirceJson,
      Dependencies.akkastream,
      Dependencies.akkaSlf4j,
      Dependencies.akkaSprayJson,
      Dependencies.geotrellisSlick,
      Dependencies.sparkCore,
      Dependencies.hadoopAws,
      Dependencies.awsSdk,
      Dependencies.scopt,
      Dependencies.ficus,
      Dependencies.dnsJava,
      Dependencies.dropbox,
      Dependencies.caffeine,
      Dependencies.scaffeine
    )
  })
  .settings(assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("shapeless.**" -> "com.azavea.shaded.shapeless.@1").inAll,
      ShadeRule.rename(
        "com.amazonaws.services.s3.**" -> "com.azavea.shaded.amazonaws.services.s3.@1"
      ).inAll,
      ShadeRule.rename(
        "com.amazonaws.**" -> "com.azavea.shaded.amazonaws.@1"
      ).inAll
    )
  )

import io.gatling.sbt.GatlingPlugin
lazy val tile = Project("tile", file("tile"))
  .dependsOn(database, datamodel, common % "test->test;compile->compile")
  .dependsOn(tool)
  .dependsOn(batch)
  .enablePlugins(GatlingPlugin)
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ testDependencies ++
    metricsDependencies ++ Seq(
      Dependencies.spark,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.akkaSprayJson,
      Dependencies.akkaCirceJson,
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
    case PathList("META-INF", "aop.xml") => aopMerge
    case _ => MergeStrategy.first
  })
  .settings(assemblyJarName in assembly := "rf-tile-server.jar")
  .settings(test in assembly := {})

lazy val tool = Project("tool", file("tool"))
  .dependsOn(bridge)
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.spark,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisRasterTestkit,
      Dependencies.shapeless,
      Dependencies.scalatest,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.circeOptics,
      Dependencies.scalaCheck
    )
  })


lazy val bridge = Project("bridge", file("bridge"))
  .settings(commonSettings:_*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.geotrellisVector
    )
  })

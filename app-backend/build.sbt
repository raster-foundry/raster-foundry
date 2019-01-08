import xerial.sbt.Sonatype._
import ReleaseTransformations._

addCommandAlias("mg", "migrations/run")

addCommandAlias(
  "gitSnapshots",
  ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

git.gitTagToVersionNumber in ThisBuild := { tag: String =>
  if (tag matches "[0-9]+\\..*") Some(tag)
  else None
}

cancelable in Global := true

lazy val commonSettings = Seq(
  // https://github.com/lucidsoftware/neo-sbt-scalafmt
  scalafmtOnCompile := true,
  // https://github.com/sksamuel/sbt-scapegoat
  scapegoatVersion in ThisBuild := Version.scapegoat,
  scalaVersion in ThisBuild := Version.scala,
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
    "-Xmax-classfile-name",
    "100",
    "-Ywarn-value-discard",
    "-Ywarn-unused",
    "-Ypartial-unification",
    "-Ypatmat-exhaust-depth",
    "100"
  ),
  // https://github.com/sbt/sbt/issues/3570
  updateOptions := updateOptions.value.withGigahorse(false),
  externalResolvers := Seq(
    "Geotoolkit Repo" at "http://maven.geotoolkit.org",
    "Open Source Geospatial Foundation Repo" at "http://download.osgeo.org/webdav/geotools/",
    "boundless" at "https://repo.boundlessgeo.com/main/",
    "imageio-ext Repository" at "http://maven.geo-solutions.it",
    DefaultMavenRepository,
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("azavea", "maven"),
    Resolver.bintrayRepo("azavea", "geotrellis"),
    Resolver.bintrayRepo("lonelyplanet", "maven"),
    Resolver.bintrayRepo("guizmaii", "maven"),
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
    Resolver.bintrayRepo("naftoligug", "maven"),
    Classpaths.sbtPluginReleases,
    Opts.resolver.sonatypeReleases,
    Resolver.bintrayIvyRepo("kamon-io", "sbt-plugins"),
    Resolver.file("local", file(Path.userHome.absolutePath + "/.ivy2/local"))(
      Resolver.ivyStylePatterns) // important to pull deps from the local repo
  ),
  shellPrompt := { s =>
    Project.extract(s).currentProject.id + " > "
  },
  // https://www.scala-sbt.org/0.13/docs/Compiler-Plugins.html
  autoCompilerPlugins := true,
  addCompilerPlugin(
    "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
) ++ publishSettings

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val publishSettings = Seq(
  // Add the default sonatype repository setting
  publishTo := sonatypePublishTo.value,
  publishMavenStyle := true,
  organization := "com.rasterfoundry",
  organizationName := "Raster Foundry",
  organizationHomepage := Some(new URL("https://www.rasterfoundry.com")),
  description := "A platform to find, combine and analyze earth imagery at any scale.",
  sonatypeProfileName := "com.rasterfoundry",
  sonatypeProjectHosting := Some(
    GitHubHosting(user = "raster-foundry",
                  repository = "raster-foundry",
                  email = "info@rasterfoundry.com")),
  developers := List(
    Developer(id = "azavea",
              name = "Azavea Inc.",
              email = "systems@azavea.com",
              url = url("https://www.azavea.com"))
  ),
  licenses := Seq(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  pgpPassphrase := Some(
    System.getenv().getOrDefault("PGP_PASSPHRASE", "").toCharArray()),
  pgpSecretRing := file("/root/.gnupg/secring.gpg"),
  usePgpKeyHex(System.getenv().getOrDefault("PGP_HEX_KEY", "0")),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    setReleaseVersion,
    releaseStepCommand("publishSigned"),
    releaseStepCommand("sonatypeReleaseAll")
  )
) ++ credentialsSettings

lazy val credentialsSettings = Seq(
  // http://web.archive.org/web/20170923125655/http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield
    Credentials("Sonatype Nexus Repository Manager",
                "oss.sonatype.org",
                username,
                password)).toSeq
)

// Create a new MergeStrategy for aop.xml files
val aopMerge = new sbtassembly.MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._

  def apply(tempDir: File,
            path: String,
            files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt = DocType("aspectj",
                     PublicID("-//AspectJ//DTD//EN",
                              "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"),
                     Nil)
    val file = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem] = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] =
      xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node] =
      xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String = xmls
      .map(x => (x \\ "aspectj" \ "weaver" \ "@options").text)
      .mkString(" ")
      .trim
    val weaverAttr =
      if (options.isEmpty) Null
      else new UnprefixedAttribute("options", options, Null)
    val aspects =
      new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver =
      new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj =
      new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

lazy val apiSettings = commonSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true,
  assemblyMergeStrategy in assembly := {
    case "reference.conf"                       => MergeStrategy.concat
    case "application.conf"                     => MergeStrategy.concat
    case n if n.startsWith("META-INF/services") => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case "META-INF/MANIFEST.MF"          => MergeStrategy.discard
    case PathList("META-INF", "aop.xml") => aopMerge
    case _                               => MergeStrategy.first
  },
  resolvers += "Open Source Geospatial Foundation Repo" at "http://download.osgeo.org/webdav/geotools/",
  resolvers += Resolver.bintrayRepo("azavea", "maven"),
  resolvers += Resolver.bintrayRepo("lonelyplanet", "maven"),
  test in assembly := {}
)
lazy val loggingDependencies = List(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7"
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
  Dependencies.scalaforklift,
  Dependencies.slickMigrationAPI
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
  Dependencies.awsStsSdk,
  Dependencies.betterFiles,
  Dependencies.commonsIO,
  Dependencies.geotrellisS3,
  Dependencies.geotrellisShapefile,
  Dependencies.betterFiles,
  Dependencies.caffeine,
  Dependencies.scaffeine,
  Dependencies.findbugAnnotations,
  Dependencies.dropbox
)

lazy val root = Project("root", file("."))
  .settings(commonSettings: _*)
  .settings(noPublishSettings)
  .aggregate(api,
             akkautil,
             db,
             common,
             migrations,
             datamodel,
             batch,
             tile,
             tool,
             bridge)

lazy val api = Project("api", file("api"))
  .dependsOn(db, datamodel, common % "test->test;compile->compile", akkautil)
  .settings(apiSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("hseeberger", "maven"))
  .settings({
    libraryDependencies ++= apiDependencies
  })

lazy val common = Project("common", file("common"))
  .dependsOn(datamodel)
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= testDependencies ++ Seq(
      Dependencies.commonsIO,
      Dependencies.caffeine,
      Dependencies.scaffeine,
      Dependencies.elasticacheClient,
      Dependencies.geotrellisS3,
      Dependencies.findbugAnnotations,
      Dependencies.chill,
      Dependencies.catsCore,
      Dependencies.awsBatchSdk,
      Dependencies.rollbar,
      Dependencies.apacheCommonsEmail
    )
  })

lazy val db = Project("db", file("db"))
  .dependsOn(datamodel % "compile->compile;test->test", common)
  .settings(commonSettings: _*)
  .settings({
    libraryDependencies ++= dbDependencies ++ loggingDependencies ++ Seq(
      Dependencies.scalatest,
      Dependencies.doobieCore,
      Dependencies.doobieHikari,
      Dependencies.doobieSpecs,
      Dependencies.doobieScalatest,
      Dependencies.doobiePostgres,
      Dependencies.doobiePostgresCirce,
      "net.postgis" % "postgis-jdbc" % "2.2.1",
      "net.postgis" % "postgis-jdbc-jtsparser" % "2.2.1",
      "org.locationtech.jts" % "jts-core" % "1.15.0",
      "com.lonelyplanet" %% "akka-http-extensions" % "0.4.15"
    )
  })

lazy val migrations = Project("migrations", file("migrations"))
  .settings(commonSettings: _*)
  .settings(noPublishSettings)
  .settings({
    libraryDependencies ++= migrationsDependencies
  })

lazy val datamodel = Project("datamodel", file("datamodel"))
  .dependsOn(tool, bridge)
  .settings(commonSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.geotrellisVectorTestkit,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisGeotools,
      Dependencies.geotools,
      Dependencies.circeCore,
      Dependencies.circeGenericExtras,
      Dependencies.scalaCheck,
      Dependencies.circeTest,
      Dependencies.jts,
      "com.lonelyplanet" %% "akka-http-extensions" % "0.4.15" % "test"
    )
  })

lazy val batch = Project("batch", file("batch"))
  .dependsOn(common, datamodel, tool, bridge, geotrellis)
  .settings(commonSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "maven"))
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= testDependencies ++ Seq(
      Dependencies.scalaLogging,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisRaster,
      Dependencies.sparkCore,
      Dependencies.hadoopAws,
      Dependencies.scopt,
      Dependencies.ficus,
      Dependencies.dnsJava,
      Dependencies.dropbox,
      Dependencies.caffeine,
      Dependencies.scaffeine,
      Dependencies.mamlJvm,
      Dependencies.mamlSpark,
      Dependencies.auth0,
      Dependencies.catsEffect,
      Dependencies.scalaCsv
    )
  })
  .settings({
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.9.2",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.2",
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.9.2"
    )
  })
  .settings(assemblyShadeRules in assembly := Seq(
    ShadeRule.rename("shapeless.**" -> "com.azavea.shaded.shapeless.@1").inAll,
    ShadeRule
      .rename(
        "com.amazonaws.services.s3.**" -> "com.azavea.shaded.amazonaws.services.s3.@1"
      )
      .inAll,
    ShadeRule
      .rename(
        "com.amazonaws.**" -> "com.azavea.shaded.amazonaws.@1"
      )
      .inAll
  ))

lazy val tile = Project("tile", file("tile"))
  .dependsOn(datamodel,
             common % "test->test;compile->compile",
             akkautil,
             geotrellis)
  .dependsOn(tool)
  .settings(fork in run := true)
  .settings(commonSettings: _*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ testDependencies ++
      metricsDependencies ++ Seq(
      Dependencies.spark,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.akkaCirceJson,
      Dependencies.akkaHttpCors,
      Dependencies.akkastream,
      Dependencies.akkaSlf4j,
      Dependencies.circeCore % "test",
      Dependencies.circeGeneric % "test",
      Dependencies.circeParser % "test",
      Dependencies.circeOptics % "test",
      Dependencies.scalajHttp % "test"
    )
  })
  .settings(assemblyMergeStrategy in assembly := {
    case m if m.toLowerCase.endsWith("manifest.mf")     => MergeStrategy.discard
    case m if m.toLowerCase.matches("meta-inf.*\\.sf$") => MergeStrategy.discard
    case "reference.conf"                               => MergeStrategy.concat
    case "application.conf"                             => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case PathList("META-INF", "aop.xml") => aopMerge
    case _                               => MergeStrategy.first
  })
  .settings(test in assembly := {})

lazy val tool = Project("tool", file("tool"))
  .dependsOn(bridge)
  .settings(commonSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "maven"))
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.sparkCore,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisRasterTestkit % "test",
      Dependencies.scalatest,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.circeOptics,
      Dependencies.scalaCheck,
      Dependencies.mamlJvm
    )
  })

lazy val geotrellis = Project("geotrellis", file("geotrellis"))
  .dependsOn(db, common, datamodel)
  .settings(commonSettings: _*)
  .settings(noPublishSettings)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisSpark,
      Dependencies.catsCore
    )
  })

lazy val akkautil = Project("akkautil", file("akkautil"))
  .dependsOn(common, db)
  .settings(commonSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.nimbusJose,
      Dependencies.akka,
      Dependencies.akkahttp,
      Dependencies.akkaCirceJson
    )
  })

lazy val bridge = Project("bridge", file("bridge"))
  .settings(commonSettings: _*)
  .settings({
    libraryDependencies ++= loggingDependencies ++ Seq(
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.geotrellisVector,
      Dependencies.scalaLogging
    )
  })

// maml / better-abstracted tile server
lazy val backsplashCore = Project("backsplash-core", file("backsplash-core"))
  .settings(commonSettings: _*)
  .settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      "io.dropwizard.metrics" % "metrics-graphite" % "4.0.3",
      "org.http4s" %% "http4s-blaze-server" % Version.http4s,
      "org.http4s" %% "http4s-circe" % Version.http4s,
      "org.http4s" %% "http4s-dsl" % Version.http4s,
      "org.http4s" %% "http4s-dropwizard-metrics" % Version.http4s,
      "org.scalatest" %% "scalatest" % Version.scalaTest,
      "com.azavea" %% "geotrellis-server-core" % Version.geotrellisServer,
      "org.scalacheck" %% "scalacheck" % Version.scalaCheck,
      "org.apache.spark" %% "spark-core" % "2.4.0" % Provided,
      "com.github.cb372" %% "scalacache-cats-effect" % "0.27.0",
      "com.github.cb372" %% "scalacache-core" % "0.27.0",
      "com.github.cb372" %% "scalacache-caffeine" % "0.27.0",
      "com.github.cb372" %% "scalacache-memcached" % "0.27.0" intransitive (),
      Dependencies.elasticacheClient,
      Dependencies.catsMeow
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )

lazy val backsplashServer = Project("backsplash-server",
                                    file("backsplash-server"))
  .dependsOn(db, backsplashCore)
  .settings(commonSettings: _*)
  .settings(noPublishSettings)
  .settings(fork in run := true)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsMeow,
      Dependencies.geotrellisServer,
      Dependencies.http4sBlaze,
      Dependencies.http4sBlazeClient,
      Dependencies.http4sCirce,
      Dependencies.http4sDSL,
      Dependencies.http4sServer,
      Dependencies.mamlJvm,
      Dependencies.nimbusJose,
      "com.github.cb372" %% "scalacache-cats-effect" % "0.27.0",
      "com.github.cb372" %% "scalacache-core" % "0.27.0",
      "com.github.cb372" %% "scalacache-caffeine" % "0.27.0"
    )
  })
  .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"))
  .settings(assemblyMergeStrategy in assembly := {
    case m if m.toLowerCase.endsWith("manifest.mf")     => MergeStrategy.discard
    case m if m.toLowerCase.matches("meta-inf.*\\.sf$") => MergeStrategy.discard
    case "reference.conf"                               => MergeStrategy.concat
    case "application.conf"                             => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case PathList("META-INF", "aop.xml") => aopMerge
    case _                               => MergeStrategy.first
  })
  .settings(assemblyJarName in assembly := "backsplash-assembly.jar")
  .settings(test in assembly := {})

import xerial.sbt.Sonatype._
import ReleaseTransformations._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

addCommandAlias(
  "gitSnapshots",
  ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\""
)

git.gitTagToVersionNumber in ThisBuild := { tag: String =>
  if (tag matches "[0-9]+\\..*") Some(tag)
  else None
}

cancelable in Global := true

val scalaOptions = Seq(
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
  "-Yrangepos",
  "-Ywarn-value-discard",
  "-Ywarn-macros:after",
  "-Ywarn-unused",
  "-Xfatal-warnings",
  "-Ywarn-unused-import",
  "-Ypartial-unification",
  "-Ybackend-parallelism",
  "4",
  "-Ypatmat-exhaust-depth",
  "100"
)

/**
  * Shared settings across all subprojects
  */
lazy val sharedSettings = Seq(
  // https://github.com/lucidsoftware/neo-sbt-scalafmt
  scalafmtOnCompile := true,
  scapegoatVersion in ThisBuild := "1.3.8",
  scalaVersion in ThisBuild := Version.scala,
  unusedCompileDependenciesFilter -= moduleFilter(
    "com.sksamuel.scapegoat",
    "scalac-scapegoat-plugin"
  ),
  unusedCompileDependenciesFilter -= moduleFilter(
    "org.apache.spark",
    "spark-core"
  ),
  // Try to keep logging sane and make sure to use slf4j + logback
  excludeDependencies ++= Seq(
    "log4j" % "log4j",
    "org.slf4j" % "slf4j-log4j12"
  ),
  scalacOptions := scalaOptions,
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
    Resolver.bintrayRepo("guizmaii", "maven"),
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
    "azavea-snapshots" at "http://nexus.internal.azavea.com/repository/azavea-snapshots/",
    Resolver.bintrayRepo("naftoligug", "maven"),
    Classpaths.sbtPluginReleases,
    Opts.resolver.sonatypeReleases,
    Resolver.bintrayIvyRepo("kamon-io", "sbt-plugins"),
    Resolver.file("local", file(Path.userHome.absolutePath + "/.ivy2/local"))(
      Resolver.ivyStylePatterns
    ) // important to pull deps from the local repo
  ),
  shellPrompt := { s =>
    Project.extract(s).currentProject.id + " > "
  },
  assemblyMergeStrategy in assembly := {
    case "reference.conf"                       => MergeStrategy.concat
    case "application.conf"                     => MergeStrategy.concat
    case n if n.startsWith("META-INF/services") => MergeStrategy.concat
    case n
        if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n
          .endsWith(".semanticdb") =>
      MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _                      => MergeStrategy.first
  },
  // https://www.scala-sbt.org/0.13/docs/Compiler-Plugins.html
  autoCompilerPlugins := true,
  addCompilerPlugin(
    "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
  ),
  addCompilerPlugin(scalafixSemanticdb), // enable SemanticDB
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")
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
    GitHubHosting(
      user = "raster-foundry",
      repository = "raster-foundry",
      email = "info@rasterfoundry.com"
    )
  ),
  developers := List(
    Developer(
      id = "azavea",
      name = "Azavea Inc.",
      email = "systems@azavea.com",
      url = url("https://www.azavea.com")
    )
  ),
  licenses := Seq(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  pgpPassphrase := Some(
    System.getenv().getOrDefault("PGP_PASSPHRASE", "").toCharArray
  ),
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
    Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      username,
      password
    )).toSeq
)

lazy val root = project
  .in(file("."))
  .settings(sharedSettings: _*)
  .settings(noPublishSettings)
  .aggregate(api,
             akkautil,
             db,
             common,
             datamodel,
             batch,
             backsplashCore,
             backsplashServer,
             backsplashExport,
             lambdaOverviews)

lazy val loggingDependencies = Seq(
  Dependencies.scalaLogging % Runtime,
  Dependencies.slf4j % Runtime,
  Dependencies.log4jOverslf4j % Runtime, // for any java classes looking for this
  Dependencies.logbackClassic % Runtime
)

/**
  * API Project Settings
  */
lazy val apiSettings = sharedSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true,
  resolvers += "Open Source Geospatial Foundation Repo" at "http://download.osgeo.org/webdav/geotools/",
  resolvers += Resolver.bintrayRepo("azavea", "maven"),
  test in assembly := {}
)

lazy val apiDependencies = Seq(
  Dependencies.akkaSlf4j,
  Dependencies.scalatest,
  Dependencies.akkaHttpCors,
  Dependencies.akkaCirceJson,
  Dependencies.awsStsSdk,
  Dependencies.betterFiles,
  Dependencies.geotrellisShapefile,
  Dependencies.betterFiles,
  Dependencies.dropbox,
  Dependencies.scalaCheck
)

lazy val api = project
  .in(file("api"))
  .dependsOn(db, common % "test->test;compile->compile", akkautil)
  .settings(apiSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("hseeberger", "maven"))
  .settings({
    libraryDependencies ++= apiDependencies ++ loggingDependencies
  })

/**
  * Lambda Overviews
  */
lazy val lambdaOverviews = project
  .in(file("lambda-overviews"))
  .dependsOn(datamodel)
  .settings(sharedSettings: _*)
  .settings(
    mainClass in assembly := Some("com.rasterfoundry.lambda.overviews.Main"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
    addCompilerPlugin(scalafixSemanticdb),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeScala = false),
    assemblyJarName in assembly := "lambda-overviews-assembly.jar"
  )
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.awsS3,
      Dependencies.awsLambdaCore,
      Dependencies.catsCore,
      Dependencies.geotrellisContribVLM,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisVector,
      Dependencies.commonsIO % Runtime,
      Dependencies.sttpCore,
      Dependencies.sttpJson,
      Dependencies.sttpCirce,
      Dependencies.circeCore,
      Dependencies.circeParser,
      Dependencies.scalatest,
      Dependencies.clistCore,
      Dependencies.clistMacros % "provided"
    ) ++ loggingDependencies
  })

/**
  * Common Settings
  */
lazy val common = project
  .in(file("common"))
  .dependsOn(datamodel)
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.elasticacheClient,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisGeotools,
      Dependencies.geotrellisVectorTestkit,
      Dependencies.logbackClassic % Runtime,
      Dependencies.mamlJvm,
      Dependencies.sparkCore,
      Dependencies.circeCore,
      Dependencies.circeParser,
      Dependencies.circeOptics,
      Dependencies.circeTest,
      Dependencies.awsBatchSdk,
      Dependencies.rollbar,
      Dependencies.apacheCommonsEmail,
      Dependencies.scalaCheck,
      Dependencies.catsScalacheck,
      Dependencies.awsLambdaSdk,
    ) ++ loggingDependencies
  })

lazy val datamodel = project
  .in(file("datamodel"))
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.shapeless,
      Dependencies.catsCore,
      Dependencies.monocleCore,
      Dependencies.circeGeneric,
      Dependencies.spray,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisVector,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisVectorTestkit,
      Dependencies.circeCore,
      Dependencies.circeParser,
      Dependencies.circeOptics,
      Dependencies.circeTest,
      Dependencies.circeGenericExtras,
      Dependencies.scalaCheck
    ) ++ loggingDependencies
  })

/**
  * DB Settings
  */
lazy val db = project
  .in(file("db"))
  .dependsOn(common % "compile->compile;test->test")
  .settings(sharedSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.scalatest,
      Dependencies.doobieCore,
      Dependencies.doobieHikari,
      Dependencies.doobiePostgres,
      Dependencies.doobiePostgresCirce,
      Dependencies.scalaCheck,
      Dependencies.postgis,
      Dependencies.flyway % Test
    ) ++ loggingDependencies
  })

/**
  * Batch Settings
  */
lazy val batch = project
  .in(file("batch"))
  .dependsOn(common, backsplashCore, geotrellis)
  .settings(sharedSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "maven"))
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.scalatest,
      Dependencies.scalaLogging,
      Dependencies.geotrellisSpark,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisRaster,
      Dependencies.sparkCore,
      Dependencies.ficus,
      Dependencies.dropbox,
      Dependencies.scopt
    ) ++ loggingDependencies
  })
  .settings({
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.9.2",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.2",
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.9.2"
    )
  })
  .settings(
    assemblyShadeRules in assembly := Seq(
      ShadeRule
        .rename("shapeless.**" -> "com.azavea.shaded.shapeless.@1")
        .inAll,
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
    )
  )

/**
  * GeoTrellis Settings
  */
lazy val geotrellis = project
  .in(file("geotrellis"))
  .dependsOn(db, common)
  .settings(sharedSettings: _*)
  .settings(noPublishSettings)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisSpark
    )
  })

/**
  * Akkautil Settings
  */
lazy val akkautil = project
  .in(file("akkautil"))
  .dependsOn(common, db, datamodel)
  .settings(sharedSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.nimbusJose,
      Dependencies.akkahttp
    )
  })

/**
  * Backsplash Core Settings
  */
lazy val backsplashCore = Project("backsplash-core", file("backsplash-core"))
  .dependsOn(common)
  .settings(sharedSettings: _*)
  .settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      Dependencies.http4sDSL,
      Dependencies.geotrellisContribVLM,
      Dependencies.geotrellisContribGDAL,
      Dependencies.geotrellisServer,
      Dependencies.scalacacheCats,
      Dependencies.scalacacheCore,
      Dependencies.scalacacheCaffeine,
      Dependencies.scalacacheMemcached,
      Dependencies.scalaCheck,
      Dependencies.elasticacheClient,
      Dependencies.geotrellisServerOgc,
      Dependencies.spatial4j
    ) ++ loggingDependencies,
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    )
  )

/**
  * Backsplash Export Settings
  */
lazy val backsplashExport =
  Project("backsplash-export", file("backsplash-export"))
    .dependsOn(common)
    .settings(sharedSettings: _*)
    .settings(
      resolvers += Resolver
        .file("local", file(Path.userHome.absolutePath + "/.ivy2/local"))(
          Resolver.ivyStylePatterns
        )
    )
    .settings(
      fork in run := true,
      libraryDependencies ++= Seq(
        Dependencies.geotrellisServer,
        Dependencies.geotrellisContribGDAL,
        Dependencies.decline,
        Dependencies.mamlJvm,
        Dependencies.geotrellisS3,
        Dependencies.geotrellisRaster,
        Dependencies.geotrellisSpark,
        Dependencies.scalaCheck,
        Dependencies.scalatest
      ) ++ loggingDependencies,
      addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
      addCompilerPlugin(
        "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
      ),
      assemblyJarName in assembly := "backsplash-export-assembly.jar"
    )

/**
  * Backsplash Server Settings
  */
lazy val backsplashServer =
  Project("backsplash-server", file("backsplash-server"))
    .dependsOn(http4sUtil, db, backsplashCore)
    .settings(sharedSettings: _*)
    .settings(noPublishSettings)
    .settings(fork in run := true)
    .settings({
      libraryDependencies ++= Seq(
        Dependencies.catsMeow,
        Dependencies.geotrellisServer,
        Dependencies.http4sBlaze,
        Dependencies.http4sCirce,
        Dependencies.http4sDSL,
        Dependencies.http4sServer,
        Dependencies.http4sXml,
        Dependencies.mamlJvm,
        Dependencies.sup,
        Dependencies.scalacacheCore,
        Dependencies.scalacacheCats,
        Dependencies.scalacacheCaffeine
      ) ++ loggingDependencies
    })
    .settings({
      dependencyOverrides ++= Seq(
        "com.azavea.gdal" % "gdal-warp-bindings" % "33.5523882"
      )
    })
    .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"))
    .settings(assemblyJarName in assembly := "backsplash-assembly.jar")
    .settings(test in assembly := {})

/**
  * http4s Utility project
  */
lazy val http4sUtil = Project("http4s-util", file("http4s-util"))
  .dependsOn(db)
  .settings(sharedSettings: _*)
  .settings(noPublishSettings)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.doobieCore,
      Dependencies.nimbusJose,
      Dependencies.scalacacheCore,
      Dependencies.scalacacheCats,
      Dependencies.scalacacheCaffeine
    )
  })
  .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"))

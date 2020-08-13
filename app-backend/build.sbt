import xerial.sbt.Sonatype._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

addCommandAlias(
  "fix",
  ";scalafix;scalafmt;scalafmtSbt"
)

scalafixDependencies in ThisBuild ++= Seq(
  "com.nequissimus" %% "sort-imports" % "0.3.2",
  "org.scalatest" %% "autofix" % "3.1.0.0"
)

cancelable in Global := true

onChangedBuildSource := ReloadOnSourceChanges

scapegoatVersion in ThisBuild := "1.3.8"

scalaVersion in ThisBuild := Version.scala

// We are overriding the default behavior of sbt-git which, by default,
// only appends the `-SNAPSHOT` suffix if there are uncommitted
// changes in the workspace.
version in ThisBuild := {
  if (git.gitHeadCommit.value.isEmpty) "dev"
  else if (git.gitCurrentTags.value.isEmpty || git.gitUncommittedChanges.value)
    git.gitDescribedVersion.value.get + "-SNAPSHOT"
  else
    git.gitDescribedVersion.value.get
}

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
  unusedCompileDependenciesFilter -= moduleFilter(
    "com.sksamuel.scapegoat",
    "scalac-scapegoat-plugin"
  ),
  unusedCompileDependenciesFilter -= moduleFilter(
    "io.jaegertracing",
    "jaeger-client"
  ),
  undeclaredCompileDependenciesFilter -= moduleFilter(
    "com.typesafe.scala-logging",
    "scala-logging"
  ),
  undeclaredCompileDependenciesFilter -= moduleFilter(
    "org.slf4j",
    "slf4j-api"
  ),
  // Try to keep logging sane and make sure to use slf4j + logback
  excludeDependencies ++= Seq(
    "log4j" % "log4j",
    "org.slf4j" % "slf4j-log4j12",
    "org.slf4j" % "slf4j-nop"
  ),
  scalacOptions := scalaOptions,
  // https://github.com/sbt/sbt/issues/3570
  scalacOptions in (Compile, console) ~= (_.filterNot(
    _ == "-Ywarn-unused-import"
  ).filterNot(_ == "-Xfatal-warnings")
    .filterNot(_ == "-Ywarn-unused")
    .filterNot(_ == "-Ywarn-unused-import")),
  updateOptions := updateOptions.value.withGigahorse(false),
  externalResolvers := Seq(
    "Geotoolkit Repo" at "https://maven.geotoolkit.org",
    "Open Source Geospatial Foundation Repo" at "https://download.osgeo.org/webdav/geotools/",
    "boundless" at "https://repo.boundlessgeo.com/main/",
    "imageio-ext Repository" at "https://maven.geo-solutions.it",
    DefaultMavenRepository,
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("colisweb", "maven"),
    Resolver.bintrayRepo("azavea", "maven"),
    Resolver.bintrayRepo("azavea", "geotrellis"),
    Resolver.bintrayRepo("guizmaii", "maven"),
    Resolver.bintrayRepo("zamblauskas", "maven"), // for scala-csv-parser
    "eclipse-snapshots" at "https://repo.eclipse.org/content/groups/snapshots",
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
    ("azavea-snapshots" at "http://nexus.internal.azavea.com/repository/azavea-snapshots/")
      .withAllowInsecureProtocol(true),
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
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
  test in assembly := {}
) ++ publishSettings

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val publishSettings = Seq(
  organization := "com.rasterfoundry",
  organizationName := "Raster Foundry",
  organizationHomepage := Some(new URL("https://rasterfoundry.azavea.com/")),
  description := "A platform to find, combine and analyze earth imagery at any scale."
) ++ sonatypeSettings ++ credentialSettings

lazy val sonatypeSettings = Seq(
  publishMavenStyle := true,
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
      url = url("https://azavea.com/")
    )
  ),
  licenses := Seq(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  publishTo := sonatypePublishToBundle.value
)

lazy val credentialSettings = Seq(
  credentials += Credentials(
    "GnuPG Key ID",
    "gpg",
    System.getenv().get("GPG_KEY_ID"),
    "ignored"
  ),
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    System.getenv().get("SONATYPE_USERNAME"),
    System.getenv().get("SONATYPE_PASSWORD")
  )
)

lazy val root = project
  .in(file("."))
  .settings(sharedSettings: _*)
  .settings(noPublishSettings)
  .aggregate(
    api,
    akkautil,
    db,
    common,
    datamodel,
    http4sUtil,
    batch,
    backsplashCore,
    backsplashServer,
    backsplashExport,
    notification
  )

lazy val loggingDependencies = Seq(
  Dependencies.scalaLogging,
  Dependencies.logbackClassic % Runtime
)

/**
  * API Project Settings
  */
lazy val apiSettings = sharedSettings ++ Seq(
  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true,
  resolvers += Resolver.bintrayRepo("azavea", "maven")
)

lazy val apiDependencies = Seq(
  Dependencies.bcrypt,
  Dependencies.betterFiles,
  Dependencies.akkaActor,
  Dependencies.akkaCirceJson,
  Dependencies.akkaHttp,
  Dependencies.akkaHttpCore,
  Dependencies.akkaHttpCors,
  Dependencies.akkaSlf4j,
  Dependencies.akkaStream,
  Dependencies.akkaStream,
  Dependencies.asyncHttpClient,
  Dependencies.awsCoreSdk,
  Dependencies.awsS3,
  Dependencies.awsStsSdk,
  Dependencies.catsCore,
  Dependencies.catsEffect,
  Dependencies.catsFree,
  Dependencies.catsKernel,
  Dependencies.circeCore,
  Dependencies.circeGeneric,
  Dependencies.doobieCore,
  Dependencies.doobieFree,
  Dependencies.doobieHikari,
  Dependencies.doobiePostgres,
  Dependencies.dropbox,
  Dependencies.geotrellisGdal,
  Dependencies.geotrellisRaster,
  Dependencies.geotrellisVector,
  Dependencies.guava,
  Dependencies.hikariCP,
  Dependencies.javaFaker,
  Dependencies.jts,
  Dependencies.nimbusJose,
  Dependencies.nimbusJoseJwt,
  Dependencies.postgres,
  Dependencies.refined,
  Dependencies.scaffeine,
  Dependencies.scalaCheck,
  Dependencies.scalatest,
  Dependencies.shapeless,
  Dependencies.sourceCode,
  Dependencies.sttpAkka,
  Dependencies.sttpAsyncBackend,
  Dependencies.sttpCatsBackend,
  Dependencies.sttpCirce,
  Dependencies.sttpCore,
  Dependencies.sttpJson,
  Dependencies.sttpModel,
  Dependencies.typesafeConfig
)

lazy val api = project
  .in(file("api"))
  .dependsOn(db, common % "test->test;compile->compile", akkautil, notification)
  .settings(apiSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("hseeberger", "maven"))
  .settings({
    libraryDependencies ++= apiDependencies ++ loggingDependencies
  })
  .settings(
    assemblyJarName in assembly := "api-assembly.jar"
  )

lazy val apiIntegrationTest = project
  .in(file("api-it"))
  .configs(IntegrationTest)
  .dependsOn(db)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.scalaCsv % "test",
      Dependencies.sttpCore % "test",
      Dependencies.sttpJson % "test",
      Dependencies.sttpCirce % "test",
      Dependencies.sttpOkHttpBackend % "test",
      Dependencies.scalatest
    )
  })
  .settings(Defaults.itSettings)
  .settings(
    unusedCompileDependenciesFilter -= moduleFilter(
      "com.sksamuel.scapegoat",
      "scalac-scapegoat-plugin"
    )
  )

/**
  * Common Settings
  */
lazy val common = project
  .in(file("common"))
  .dependsOn(datamodel)
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.awsBatchSdk,
      Dependencies.awsCoreSdk,
      Dependencies.awsS3,
      Dependencies.catsCore,
      Dependencies.catsScalacheck,
      Dependencies.chronoscala,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeOptics,
      Dependencies.circeParser,
      Dependencies.circeTest,
      Dependencies.commonsIO,
      Dependencies.elasticacheClient,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisStore,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisVector,
      Dependencies.geotrellisVectorTestkit,
      Dependencies.jts,
      Dependencies.logbackClassic % Runtime,
      Dependencies.mamlJvm,
      Dependencies.monocleCore,
      Dependencies.rollbar,
      Dependencies.scalaCheck,
      Dependencies.shapeless,
      Dependencies.spireMath,
      Dependencies.typesafeConfig
    ) ++ loggingDependencies
  })

lazy val datamodel = project
  .in(file("datamodel"))
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.awsS3,
      Dependencies.catsCore,
      Dependencies.catsKernel,
      Dependencies.catsLaws,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeGenericExtras,
      Dependencies.circeOptics,
      Dependencies.circeParser,
      Dependencies.circeRefined,
      Dependencies.circeTest,
      Dependencies.disciplineScalatest,
      Dependencies.geotrellisGdal,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisVector,
      Dependencies.geotrellisVectorTestkit,
      Dependencies.jts,
      Dependencies.monocleCore,
      Dependencies.refined,
      Dependencies.scalaCheck,
      Dependencies.shapeless,
      Dependencies.spireMath,
      Dependencies.stac4s
    ) ++ loggingDependencies
  })

/**
  * DB Settings
  */
lazy val db = project
  .in(file("db"))
  .dependsOn(common % "compile->compile;test->test", notification)
  .settings(name := "database")
  .settings(sharedSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.apacheCommonsEmail,
      Dependencies.awsCoreSdk,
      Dependencies.awsS3,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsFree,
      Dependencies.catsKernel,
      Dependencies.circeCore,
      Dependencies.commonsCodec,
      Dependencies.doobieCore,
      Dependencies.doobieFree,
      Dependencies.doobieFree,
      Dependencies.doobieHikari,
      Dependencies.doobiePostgres,
      Dependencies.doobiePostgresCirce,
      Dependencies.doobieRefined,
      Dependencies.doobieScalatest,
      Dependencies.elasticacheClient,
      Dependencies.flyway % Test,
      Dependencies.fs2,
      Dependencies.geotrellisGdal,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisStore,
      Dependencies.geotrellisVector,
      Dependencies.guava,
      Dependencies.hikariCP,
      Dependencies.jts,
      Dependencies.mamlJvm,
      Dependencies.monocleCore % "test",
      Dependencies.newtype,
      Dependencies.postgis,
      Dependencies.postgres,
      Dependencies.refined,
      Dependencies.scalaCheck,
      Dependencies.scalaCheck,
      Dependencies.scalacacheCaffeine,
      Dependencies.scalacacheCats,
      Dependencies.scalacacheCirce,
      Dependencies.scalacacheCore,
      Dependencies.scalacacheMemcached,
      Dependencies.scalatestplusScalaCheck,
      Dependencies.shapeless,
      Dependencies.sourceCode,
      Dependencies.stac4s,
      Dependencies.typesafeConfig
    ) ++ loggingDependencies
  })
  .settings(testOptions in Test += Tests.Argument("-oD"))

/**
  * Batch Settings
  */
lazy val batch = project
  .in(file("batch"))
  .dependsOn(common, backsplashCore, notification)
  .settings(sharedSettings: _*)
  .settings(resolvers += Resolver.bintrayRepo("azavea", "maven"))
  .settings(resolvers += Resolver.bintrayRepo("azavea", "geotrellis"))
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.asyncHttpClient,
      Dependencies.awsCoreSdk,
      Dependencies.awsS3,
      Dependencies.betterFiles,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsFree,
      Dependencies.catsKernel,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeOptics,
      Dependencies.circeParser,
      Dependencies.commonsIO,
      Dependencies.doobieCore,
      Dependencies.doobieFree,
      Dependencies.doobieHikari,
      Dependencies.doobiePostgres,
      Dependencies.dropbox,
      Dependencies.ficus,
      Dependencies.fs2,
      Dependencies.fs2,
      Dependencies.geotrellisGdal,
      Dependencies.geotrellisLayer,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisVector,
      Dependencies.guava,
      Dependencies.hadoop,
      Dependencies.hikariCP,
      Dependencies.jts,
      Dependencies.monocleCore,
      Dependencies.refined,
      Dependencies.scalatest,
      Dependencies.scopt,
      Dependencies.shapeless,
      Dependencies.sourceCode,
      Dependencies.spireMath,
      Dependencies.stac4s,
      Dependencies.sttpAsyncBackend,
      Dependencies.sttpCatsBackend,
      Dependencies.sttpCore,
      Dependencies.typesafeConfig
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
  .settings(assemblyJarName in assembly := "batch-assembly.jar")

/**
  * Akkautil Settings
  */
lazy val akkautil = project
  .in(file("akkautil"))
  .dependsOn(common, db, datamodel)
  .settings(sharedSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.akkaCirceJson,
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,
      Dependencies.awsS3,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsFree,
      Dependencies.catsKernel,
      Dependencies.circeCore,
      Dependencies.doobieCore,
      Dependencies.doobieFree,
      Dependencies.jsonSmart,
      Dependencies.nimbusJose,
      Dependencies.nimbusJoseJwt,
      Dependencies.postgres,
      Dependencies.typesafeConfig
    )
  })

/**
  * Backsplash Core Settings
  */
lazy val backsplashCore = Project("backsplash-core", file("backsplash-core"))
  .dependsOn(common, db)
  .settings(sharedSettings: _*)
  .settings(
    fork in run := true,
    libraryDependencies ++= Seq(
      Dependencies.awsS3,
      Dependencies.awsUtilsSdkV2,
      Dependencies.awsS3SdkV2,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsFree,
      Dependencies.catsKernel,
      Dependencies.circeCore,
      Dependencies.circeParser,
      Dependencies.doobieCore,
      Dependencies.doobieFree,
      Dependencies.geotrellisGdal,
      Dependencies.geotrellisLayer,
      Dependencies.geotrellisProj4,
      Dependencies.geotrellisRaster,
      Dependencies.geotrellisS3,
      Dependencies.geotrellisServer,
      Dependencies.geotrellisUtil,
      Dependencies.geotrellisVector,
      Dependencies.http4sCore,
      Dependencies.http4sDSL,
      Dependencies.jts,
      Dependencies.mamlJvm,
      Dependencies.opentracingCore,
      Dependencies.opentracingContext,
      Dependencies.scalaCheck,
      Dependencies.scalacacheCaffeine,
      Dependencies.scalacacheCats,
      Dependencies.scalacacheCore,
      Dependencies.spatial4j,
      Dependencies.spireMath,
      Dependencies.typesafeConfig,
      "org.apache.httpcomponents" % "httpclient" % "4.5.9",
      "org.apache.httpcomponents" % "httpcore" % "4.4.11"
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
        Dependencies.awsS3,
        Dependencies.awsCoreSdk,
        Dependencies.catsCore,
        Dependencies.catsEffect,
        Dependencies.circeCore,
        Dependencies.circeParser,
        Dependencies.circeShapes,
        Dependencies.commonsIO,
        Dependencies.decline,
        Dependencies.geotrellisGdal,
        Dependencies.geotrellisLayer,
        Dependencies.geotrellisProj4,
        Dependencies.geotrellisRaster,
        Dependencies.geotrellisServer,
        Dependencies.geotrellisVector,
        Dependencies.jts,
        Dependencies.mamlJvm,
        Dependencies.scalaCheck,
        Dependencies.scalajHttp,
        Dependencies.scalatestplusScalaCheck,
        Dependencies.scalatest,
        Dependencies.shapeless,
        Dependencies.typesafeConfig
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
        Dependencies.awsS3,
        Dependencies.catsCore,
        Dependencies.catsEffect,
        Dependencies.catsFree,
        Dependencies.catsMeow,
        Dependencies.circeCore,
        Dependencies.circeGeneric,
        Dependencies.circeParser,
        Dependencies.cron4s,
        Dependencies.doobieCore,
        Dependencies.doobieFree,
        Dependencies.doobieHikari,
        Dependencies.fs2,
        Dependencies.fs2Cron,
        Dependencies.geotrellisLayer,
        Dependencies.geotrellisProj4,
        Dependencies.geotrellisRaster,
        Dependencies.geotrellisServer,
        Dependencies.geotrellisVector,
        Dependencies.guava,
        Dependencies.hikariCP,
        Dependencies.http4sBlaze,
        Dependencies.http4sCirce,
        Dependencies.http4sCore,
        Dependencies.http4sDSL,
        Dependencies.http4sServer,
        Dependencies.jts,
        Dependencies.mamlJvm,
        Dependencies.opentracingApi,
        Dependencies.opentracingCore,
        Dependencies.opentracingContext,
        Dependencies.scalacacheCaffeine,
        Dependencies.scalacacheCats,
        Dependencies.scalacacheCore,
        Dependencies.shapeless,
        Dependencies.sourceCode,
        Dependencies.sup,
        Dependencies.typesafeConfig,
        Dependencies.vault
      ) ++ loggingDependencies
    })
    .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"))
    .settings(assemblyJarName in assembly := "backsplash-assembly.jar")

/**
  * http4s Utility project
  */
lazy val http4sUtil = Project("http4s-util", file("http4s-util"))
  .dependsOn(db)
  .settings(sharedSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.awsXrayRecorder,
      Dependencies.awsXraySdk,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsFree,
      Dependencies.catsKernel,
      Dependencies.circeCore,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.doobieCore,
      Dependencies.doobieFree,
      Dependencies.http4sCore,
      Dependencies.jaegerClient,
      Dependencies.jaegerCore,
      Dependencies.nimbusJose,
      Dependencies.nimbusJoseJwt,
      Dependencies.opentracingApi,
      Dependencies.opentracingCore,
      Dependencies.opentracingContext,
      Dependencies.scalacacheCaffeine,
      Dependencies.scalacacheCats,
      Dependencies.scalacacheCore,
      Dependencies.shapeless,
      Dependencies.typesafeConfig
    )
  })
  .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"))

/** Notification project
  *
  * For holding all of our shared code related to letting people know about things
  */
lazy val notification = Project("notification", file("notification"))
  .dependsOn(datamodel)
  .settings(sharedSettings: _*)
  .settings({
    libraryDependencies ++= Seq(
      Dependencies.apacheCommonsEmail,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.circeCore,
      Dependencies.javaMail,
      Dependencies.log4cats,
      Dependencies.log4catsSlf4j,
      Dependencies.newtype,
      Dependencies.sttpCore,
      Dependencies.sttpJson,
      Dependencies.sttpAsyncBackend,
      Dependencies.sttpCirce,
      Dependencies.sttpModel
    )
  })

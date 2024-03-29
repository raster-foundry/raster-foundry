import scala.util.Properties

import sbt._

object Version {
  val akka = "2.6.4"
  val akkaCirceJson = "1.32.0"
  val akkaHttp = "10.2.6"
  val akkaHttpCors = "0.2.2"
  val akkaSlf4j = "2.4.13"
  val algebra = "2.0.1"
  val apacheAvro = "1.8.2"
  val apacheCommonsEmail = "1.5"
  val auth0 = "1.5.0"
  val awsBatchSdk = "1.11.763"
  val awsCoreSdk = "1.11.763"
  val awsLambdaCore = "1.11.763"
  val awsSdkVersion = "1.11.763"
  val awsSdkV2Version = "2.16.13"
  val awsXrayRecorder = "2.8.0"
  val betterFiles = "3.4.0"
  val bcrypt = "4.1"
  val caffeine = "2.3.5"
  val cats = "2.6.1"
  val catsEffect = "2.5.1"
  val catsMeow = "0.4.0"
  val catsScalacheck = "0.2.0"
  val chronoscala = "1.0.0"
  val circe = "0.14.1"
  val commonsCodec = "1.11"
  val commonsIO = "2.8.0"
  val cron4s = "0.6.0"
  val decline = "0.6.0"
  val disciplineScalatest = "1.0.1"
  val dnsJava = "2.1.8"
  val doobie = "0.9.0"
  val dropbox = "3.0.9"
  val elasticacheClient = "1.1.1"
  val ficus = "1.4.0"
  val flyway = "6.0.8"
  val fs2 = "2.5.9"
  val fs2Cron = "0.2.2"
  val geotrellis = "3.6.0"
  val geotrellisServer = "4.5.0"
  val guava = "20.0"
  val hadoop = "2.8.4"
  val hikariCP = "3.4.2"
  val http4s = "0.21.25"
  val httpComponentsClient = "4.5.13"
  val httpComponentsCore = "4.4.13"
  val jaegerClient = "1.0.0"
  val jaegerCore = "1.0.0"
  val javaFaker = "1.0.2"
  val javaMail = "1.5.6"
  val json4s = "3.5.0"
  val jts = "1.16.1"
  val log4cats = "1.1.1"
  val logback = "1.2.3"
  val maml = "0.6.1"
  val monocle = "2.1.0"
  val newtype = "0.4.3"
  val nimbusJose = "1.0.2"
  val nimbusJoseJwt = "9.8.1"
  val opentracingVersion = "3.1.0"
  val opentracingApi = "0.33.0"
  val postgis = "2.2.1"
  val postgres = "42.2.12"
  val refined = "0.9.27"
  val rollbar = "1.4.0"
  val scaffeine = "4.0.2"
  val scala = "2.12.10"
  val scalaCheck = "1.14.1"
  val scalaCsv = "1.3.6"
  val scalaLogging = "3.9.2"
  val scalaTest = "3.1.1"
  val scalacache = "0.28.0"
  val scalajHttp = "2.4.2"
  val scalatestPlus = "3.1.1.1"
  val scapegoat = "1.3.7"
  val scopt = "3.5.0"
  val slf4j = "1.7.25"
  val sourceCode = "0.2.1"
  val spatial4j = "0.7"
  val spire = "0.17.0"
  val stac4s = "0.6.2"
  val sttp = "3.3.16"
  val sttpModel = "1.4.16"
  val sttpShared = "1.2.7"
  val sup = "0.7.0"
  val typesafeConfig = "1.4.0"
  val vault = "2.0.0"
}

object Dependencies {
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
  val akkaCirceJson =
    "de.heikoseeberger" %% "akka-http-circe" % Version.akkaCirceJson
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % Version.akkaHttp
  val akkaHttpCors = "ch.megard" %% "akka-http-cors" % Version.akkaHttpCors
  val akkaSlf4j =
    "com.typesafe.akka" %% "akka-slf4j" % Version.akkaSlf4j % Runtime
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
  val akkatestkit =
    "com.typesafe.akka" %% "akka-http-testkit" % Version.akkaHttp
  val algebra = "org.typelevel" %% "algebra" % Version.algebra
  val apacheAvro = "org.apache.avro" % "avro" % Version.apacheAvro
  val apacheCommonsEmail =
    "org.apache.commons" % "commons-email" % Version.apacheCommonsEmail
  val apacheHttpCore =
    "org.apache.httpcomponents" % "httpcore" % Version.httpComponentsCore
  val apacheHttpClient =
    "org.apache.httpcomponents" % "httpclient" % Version.httpComponentsClient
  val auth0 = "com.auth0" % "auth0" % Version.auth0
  val awsBatchSdk = "com.amazonaws" % "aws-java-sdk-batch" % Version.awsBatchSdk
  val awsCoreSdk = "com.amazonaws" % "aws-java-sdk-core" % Version.awsCoreSdk
  val awsLambdaCore =
    "com.amazonaws" % "aws-lambda-java-core" % Version.awsLambdaCore
  val awsS3 = "com.amazonaws" % "aws-java-sdk-s3" % Version.awsSdkVersion
  val awsStsSdk = "com.amazonaws" % "aws-java-sdk-sts" % Version.awsSdkVersion
  val awsXrayRecorder =
    "com.amazonaws" % "aws-xray-recorder-sdk-core" % Version.awsXrayRecorder
  val awsXraySdk = "com.amazonaws" % "aws-java-sdk-xray" % Version.awsSdkVersion
  val awsS3SdkV2 = "software.amazon.awssdk" % "s3" % Version.awsSdkV2Version
  val awsUtilsSdkV2 =
    "software.amazon.awssdk" % "utils" % Version.awsSdkV2Version
  val betterFiles =
    "com.github.pathikrit" %% "better-files" % Version.betterFiles
  val bcrypt = "com.github.t3hnar" %% "scala-bcrypt" % Version.bcrypt
  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % Version.caffeine
  val catsCore = "org.typelevel" %% "cats-core" % Version.cats
  val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect
  val catsFree = "org.typelevel" %% "cats-free" % Version.cats
  val catsKernel = "org.typelevel" %% "cats-kernel" % Version.cats
  val catsLaws = "org.typelevel" %% "cats-laws" % Version.cats % Test
  val catsMeow = "com.olegpy" %% "meow-mtl-core" % Version.catsMeow
  val catsScalacheck =
    "io.chrisdavenport" %% "cats-scalacheck" % Version.catsScalacheck % "test"
  val chronoscala = "jp.ne.opt" %% "chronoscala" % Version.chronoscala
  val circeCore = "io.circe" %% "circe-core" % Version.circe
  val circeGeneric = "io.circe" %% "circe-generic" % Version.circe
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % Version.circe
  val circeOptics = "io.circe" %% "circe-optics" % Version.circe
  val circeParser = "io.circe" %% "circe-parser" % Version.circe
  val circeRefined = "io.circe" %% "circe-refined" % Version.circe
  val circeShapes = "io.circe" %% "circe-shapes" % Version.circe
  val circeTest = "io.circe" %% "circe-testing" % Version.circe % "test"
  val clistCore = "org.backuity.clist" %% "clist-core" % "3.5.0"
  val clistMacros = "org.backuity.clist" %% "clist-macros" % "3.5.0"
  val commonsCodec = "commons-codec" % "commons-codec" % Version.commonsCodec
  val commonsIO = "commons-io" % "commons-io" % Version.commonsIO
  val cron4s = "com.github.alonsodomin.cron4s" %% "cron4s-core" % Version.cron4s
  val decline = "com.monovore" %% "decline" % Version.decline
  val disciplineScalatest =
    "org.typelevel" %% "discipline-scalatest" % Version.disciplineScalatest % "test"
  val doobieCore = "org.tpolecat" %% "doobie-core" % Version.doobie
  val doobieFree = "org.tpolecat" %% "doobie-free" % Version.doobie
  val doobieHikari = "org.tpolecat" %% "doobie-hikari" % Version.doobie
  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % Version.doobie
  val doobiePostgresCirce =
    "org.tpolecat" %% "doobie-postgres-circe" % Version.doobie
  val doobieRefined = "org.tpolecat" %% "doobie-refined" % Version.doobie
  val doobieScalatest =
    "org.tpolecat" %% "doobie-scalatest" % Version.doobie % "test"
  val dropbox = "com.dropbox.core" % "dropbox-core-sdk" % Version.dropbox
  val elasticacheClient =
    "com.amazonaws" % "elasticache-java-cluster-client" % Version.elasticacheClient
  val ficus = "com.iheart" %% "ficus" % Version.ficus
  val flyway = "org.flywaydb" % "flyway-core" % Version.flyway
  val fs2 = "co.fs2" %% "fs2-core" % Version.fs2
  val fs2io = "co.fs2" %% "fs2-io" % Version.fs2
  val fs2Cron = "eu.timepit" %% "fs2-cron-core" % Version.fs2Cron
  val geotrellisGdal =
    "org.locationtech.geotrellis" %% "geotrellis-gdal" % Version.geotrellis
  val geotrellisLayer =
    "org.locationtech.geotrellis" %% "geotrellis-layer" % Version.geotrellis
  val geotrellisProj4 =
    "org.locationtech.geotrellis" %% "geotrellis-proj4" % Version.geotrellis
  val geotrellisRaster =
    "org.locationtech.geotrellis" %% "geotrellis-raster" % Version.geotrellis
  val geotrellisS3 =
    "org.locationtech.geotrellis" %% "geotrellis-s3" % Version.geotrellis
  val geotrellisServer =
    "com.azavea.geotrellis" %% "geotrellis-server-core" % Version.geotrellisServer
  val geotrellisStore =
    "org.locationtech.geotrellis" %% "geotrellis-store" % Version.geotrellis
  val geotrellisUtil =
    "org.locationtech.geotrellis" %% "geotrellis-util" % Version.geotrellis
  val geotrellisVector =
    "org.locationtech.geotrellis" %% "geotrellis-vector" % Version.geotrellis
  val geotrellisVectorTestkit =
    "org.locationtech.geotrellis" %% "geotrellis-vector-testkit" % Version.geotrellis % "test"
  val geotrellisVectorTile =
    "org.locationtech.geotrellis" %% "geotrellis-vectortile" % Version.geotrellis
  val guava = "com.google.guava" % "guava" % Version.guava
  val hadoop = "org.apache.hadoop" % "hadoop-common" % Version.hadoop
  val hikariCP = "com.zaxxer" % "HikariCP" % Version.hikariCP
  val http4sBlaze = "org.http4s" %% "http4s-blaze-server" % Version.http4s
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % Version.http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % Version.http4s
  val http4sClient = "org.http4s" %% "http4s-client" % Version.http4s
  val http4sCore = "org.http4s" %% "http4s-core" % Version.http4s
  val http4sDSL = "org.http4s" %% "http4s-dsl" % Version.http4s
  val http4sServer = "org.http4s" %% "http4s-server" % Version.http4s
  val http4sXml = "org.http4s" %% "http4s-scala-xml" % Version.http4s
  val jaegerClient = "io.jaegertracing" % "jaeger-client" % "1.0.0"
  val jaegerCore = "io.jaegertracing" % "jaeger-core" % Version.jaegerCore
  val javaFaker = "com.github.javafaker" % "javafaker" % Version.javaFaker
  val javaMail = "com.sun.mail" % "javax.mail" % Version.javaMail
  val jts = "org.locationtech.jts" % "jts-core" % Version.jts
  val log4cats = "io.chrisdavenport" %% "log4cats-core" % Version.log4cats
  val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j" % Version.log4cats
  val log4jOverslf4j = "org.slf4j" % "slf4j-simple" % Version.slf4j
  val logbackClassic = "ch.qos.logback" % "logback-classic" % Version.logback
  val mamlJvm = "com.azavea.geotrellis" %% "maml-jvm" % Version.maml
  val monocleCore =
    "com.github.julien-truffaut" %% "monocle-core" % Version.monocle
  val monocleMacro =
    "com.github.julien-truffaut" %% "monocle-macro" % Version.monocle
  val newtype = "io.estatico" %% "newtype" % Version.newtype
  val nimbusJose =
    "com.guizmaii" %% "scala-nimbus-jose-jwt" % Version.nimbusJose
  val nimbusJoseJwt = "com.nimbusds" % "nimbus-jose-jwt" % Version.nimbusJoseJwt
  val opentracingApi =
    "io.opentracing" % "opentracing-api" % Version.opentracingApi
  val opentracingCore =
    "com.colisweb" %% "scala-opentracing-core" % Version.opentracingVersion
  val opentracingContext =
    "com.colisweb" %% "scala-opentracing-context" % Version.opentracingVersion
  val opentracingClient =
    "com.colisweb" %% "scala-opentracing-http4s-client-blaze" % Version.opentracingVersion
  val postgis = "net.postgis" % "postgis-jdbc" % Version.postgis
  val postgres = "org.postgresql" % "postgresql" % Version.postgres
  val refined = "eu.timepit" %% "refined" % Version.refined
  val rollbar = "com.rollbar" % "rollbar-java" % Version.rollbar
  val scaffeine = "com.github.blemale" %% "scaffeine" % Version.scaffeine
  val scalaCheck =
    "org.scalacheck" %% "scalacheck" % Version.scalaCheck % "test"
  val scalaLogging =
    "com.typesafe.scala-logging" %% "scala-logging" % Version.scalaLogging
  val scalacacheCaffeine =
    "com.github.cb372" %% "scalacache-caffeine" % Version.scalacache
  val scalacacheCats =
    "com.github.cb372" %% "scalacache-cats-effect" % Version.scalacache
  val scalacacheCirce =
    "com.github.cb372" %% "scalacache-circe" % Version.scalacache
  val scalacacheCore =
    "com.github.cb372" %% "scalacache-core" % Version.scalacache
  val scalacacheMemcached =
    "com.github.cb372" %% "scalacache-memcached" % Version.scalacache intransitive ()
  val scalaCsv = "com.github.tototoshi" %% "scala-csv" % Version.scalaCsv
  val scalajHttp = "org.scalaj" %% "scalaj-http" % Version.scalajHttp
  val scalatest = "org.scalatest" %% "scalatest" % Version.scalaTest % "test"
  val scalatestplusScalaCheck =
    "org.scalatestplus" %% "scalacheck-1-14" % Version.scalatestPlus % "test"
  val scopt = "com.github.scopt" %% "scopt" % Version.scopt
  val shapeless = "com.chuusai" %% "shapeless" % "2.3.7"
  val slf4j = "org.slf4j" % "slf4j-api" % Version.slf4j
  val sourceCode = "com.lihaoyi" %% "sourcecode" % Version.sourceCode
  val spatial4j = "org.locationtech.spatial4j" % "spatial4j" % Version.spatial4j
  val spire = "org.typelevel" %% "spire" % Version.spire
  val stac4s = "com.azavea.stac4s" %% "core" % Version.stac4s
  val sttpSharedCore =
    "com.softwaremill.sttp.shared" %% "core" % Version.sttpShared
  val sttpSharedAkka =
    "com.softwaremill.sttp.shared" %% "akka" % Version.sttpShared
  val sttpModel = "com.softwaremill.sttp.model" %% "core" % Version.sttpModel
  val sttpAkka =
    "com.softwaremill.sttp.client3" %% "akka-http-backend" % Version.sttp
  val sttpAsyncBackend =
    "com.softwaremill.sttp.client3" %% "async-http-client-backend" % Version.sttp
  val sttpCirce = "com.softwaremill.sttp.client3" %% "circe" % Version.sttp
  val sttpCore = "com.softwaremill.sttp.client3" %% "core" % Version.sttp
  val sttpJson = "com.softwaremill.sttp.client3" %% "json-common" % Version.sttp
  val sup = "com.kubukoz" %% "sup-core" % Version.sup
  val typesafeConfig = "com.typesafe" % "config" % Version.typesafeConfig
  val vault = "io.chrisdavenport" %% "vault" % Version.vault
}

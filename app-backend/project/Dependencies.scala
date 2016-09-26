import scala.util.Properties

import sbt._

object Dependencies {
  val akka               = "com.typesafe.akka"     %% "akka-actor" % Version.akka
  val akkahttp           = "com.typesafe.akka"     %% "akka-http-experimental" % Version.akka
  val akkajson           = "com.typesafe.akka"     %% "akka-http-spray-json-experimental" % Version.akka
  val akkastream         = "com.typesafe.akka"     %% "akka-stream" % Version.akka
  val akkatestkit        = "com.typesafe.akka"     %% "akka-http-testkit" % Version.akka
  val hikariCP           = "com.typesafe.slick"    %% "slick-hikaricp" % Version.hikariCP
  val postgres           = "org.postgresql"         % "postgresql" % Version.postgres
  val scalaforklift      = "com.liyaos"            %% "scala-forklift-slick" % Version.scalaForklift
  val scalatest          = "org.scalatest"         %% "scalatest" % Version.scalaTest % "test"
  val slf4j              = "org.slf4j"              % "slf4j-simple" % Version.slf4j
  val slick              = "com.typesafe.slick"    %% "slick" % Version.slick
  val slickPG            = "com.github.tminglei"   %% "slick-pg" % Version.slickPG
  val slickPGSpray       = "com.github.tminglei"   %% "slick-pg_spray-json" % Version.slickPG
  val authCommon         = "de.choffmeister"       %% "auth-common" % Version.akkaAuth
  val authAkka           = "de.choffmeister"       %% "auth-akka-http" % Version.akkaAuth
  val akkaHttpExtensions = "com.lonelyplanet"      %% "akka-http-extensions" % Version.akkaHttpExtensions
  val akkaHttpCors       = "ch.megard"             %% "akka-http-cors" % Version.akkaHttpCors
  val ammoniteOps        = "com.lihaoyi"           %% "ammonite-ops" % Version.ammoniteOps
  val geotrellisSparkEtl = "com.azavea.geotrellis" %% "geotrellis-spark-etl" % Version.geotrellis
  val geotrellisS3       = "com.azavea.geotrellis" %% "geotrellis-s3" % Version.geotrellis
  val geotrellisRaster   = "com.azavea.geotrellis" %% "geotrellis-raster" % Version.geotrellis
  val geotrellisVector   = "com.azavea.geotrellis" %% "geotrellis-vector" % Version.geotrellis
  val awsS3              = "com.amazonaws"          % "aws-java-sdk-s3"   % Version.aws
  val spark              = "org.apache.spark"      %% "spark-core"        % Version.spark % "provided"
}

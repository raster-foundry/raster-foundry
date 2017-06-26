import scala.util.Properties

import sbt._

object Dependencies {
  val akka                    = "com.typesafe.akka"           %% "akka-actor"                        % Version.akka
  val akkahttp                = "com.typesafe.akka"           %% "akka-http"                         % Version.akkaHttp
  val akkaSprayJson           = "com.typesafe.akka"           %% "akka-http-spray-json"              % Version.akkaHttp
  val akkatestkit             = "com.typesafe.akka"           %% "akka-http-testkit"                 % Version.akkaHttp
  val akkastream              = "com.typesafe.akka"           %% "akka-stream"                       % Version.akka
  val akkaSlf4j               = "com.typesafe.akka"           %% "akka-slf4j"                        % Version.akkaSlf4j
  val geotrellisSparkEtl      = "org.locationtech.geotrellis" %% "geotrellis-spark-etl"              % Version.geotrellis
  val geotrellisSpark         = "org.locationtech.geotrellis" %% "geotrellis-spark"                  % Version.geotrellis
  val geotrellisS3            = "org.locationtech.geotrellis" %% "geotrellis-s3"                     % Version.geotrellis
  val geotrellisRaster        = "org.locationtech.geotrellis" %% "geotrellis-raster"                 % Version.geotrellis
  val geotrellisRasterTestkit = "org.locationtech.geotrellis" %% "geotrellis-raster-testkit"         % Version.geotrellis
  val geotrellisSlick         = "org.locationtech.geotrellis" %% "geotrellis-slick"                  % Version.geotrellis
  val geotrellisVector        = "org.locationtech.geotrellis" %% "geotrellis-vector"                 % Version.geotrellis
  val geotrellisUtil          = "org.locationtech.geotrellis" %% "geotrellis-util"                   % Version.geotrellis
  val spark                   = "org.apache.spark"            %% "spark-core"                        % Version.spark % "provided"
  val sparkCore               = "org.apache.spark"            %% "spark-core"                        % Version.spark
  val hadoopAws               = "org.apache.hadoop"            % "hadoop-aws"                        % Version.hadoop
  val awsSdk                  = "com.amazonaws"                % "aws-java-sdk"                      % Version.awsSdk
  val hikariCP                = "com.typesafe.slick"          %% "slick-hikaricp"                    % Version.hikariCP
  val postgres                = "org.postgresql"               % "postgresql"                        % Version.postgres
  val scalaforklift           = "com.liyaos"                  %% "scala-forklift-slick"              % Version.scalaForklift
  val scalatest               = "org.scalatest"               %% "scalatest"                         % Version.scalaTest % "test"
  val slf4j                   = "org.slf4j"                    % "slf4j-simple"                      % Version.slf4j
  val scalaLogging            = "com.typesafe.scala-logging"  %% "scala-logging"                     % Version.scalaLogging
  val slick                   = "com.typesafe.slick"          %% "slick"                             % Version.slick force()
  val slickPG                 = "com.github.tminglei"         %% "slick-pg"                          % Version.slickPG
  val slickPGSpray            = "com.github.tminglei"         %% "slick-pg_spray-json"               % Version.slickPG
  val slickPGCirce            = "com.github.tminglei"         %% "slick-pg_circe-json"               % Version.slickPG
  val json4s                  = "org.json4s"                  %% "json4s-jackson"                    % Version.json4s
  val jwtCore                 = "com.pauldijou"               %% "jwt-core"                          % Version.jwtCore
  val jwtJson                 = "com.pauldijou"               %% "jwt-json4s-jackson"                % Version.jwtJson
  val akkaHttpExtensions      = "com.lonelyplanet"            %% "akka-http-extensions"              % Version.akkaHttpExtensions
  val akkaHttpCors            = "ch.megard"                   %% "akka-http-cors"                    % Version.akkaHttpCors
  val ammoniteOps             = "com.lihaoyi"                 %% "ammonite-ops"                      % Version.ammoniteOps
  val commonsIO               = "commons-io"                   % "commons-io"                        % Version.commonsIO
  val scopt                   = "com.github.scopt"            %% "scopt"                             % Version.scopt
  val caffeine                = "com.github.ben-manes.caffeine" % "caffeine"                         % Version.caffeine
  val scaffeine               = "com.github.blemale"          %% "scaffeine"                         % Version.scaffeine
  val elasticacheClient       = "com.amazonaws"                % "elasticache-java-cluster-client"   % Version.elasticacheClient
  val shapeless               = "com.chuusai"                 %% "shapeless"                         % Version.shapeless
  val findbugAnnotations      = "com.google.code.findbugs"     % "annotations"                       % Version.findbugAnnotations % "compile"
  val chill                   = "com.twitter"                 %% "chill"                             % Version.chill
  val circeCore               = "io.circe"                    %% "circe-core"                        % Version.circe
  val circeGeneric            = "io.circe"                    %% "circe-generic"                     % Version.circe
  val circeGenericExtras      = "io.circe"                    %% "circe-generic-extras"              % Version.circe
  val circeParser             = "io.circe"                    %% "circe-parser"                      % Version.circe
  val circeOptics             = "io.circe"                    %% "circe-optics"                      % Version.circe
  val akkaCirceJson           = "de.heikoseeberger"           %% "akka-http-circe"                   % Version.akkaCirceJson
  val cats                    = "org.typelevel"               %% "cats"                              % Version.cats
  val gatlingHighcharts       = "io.gatling.highcharts"        % "gatling-charts-highcharts"         % Version.gatling
  val gatlingTest             = "io.gatling"                   % "gatling-test-framework"            % Version.gatling % "test,it"
  val gatlingApp              = "io.gatling"                   % "gatling-app"                       % Version.gatling % "test,it"
  val scalajHttp              = "org.scalaj"                  %% "scalaj-http"                       % Version.scalajHttp
  val ficus                   = "com.iheart"                  %% "ficus"                             % Version.ficus
  val dnsJava                 = "dnsjava"                      % "dnsjava"                           % Version.dnsJava
  val dropbox                 = "com.dropbox.core"             % "dropbox-core-sdk"                  % Version.dropbox
  val scalaCheck              = "org.scalacheck"              %% "scalacheck"                        % Version.scalaCheck % "test"
  val kamonCore               = "io.kamon"                    %% "kamon-core"                        % Version.kamon
  val kamonAkka               = "io.kamon"                    %% "kamon-akka"                        % Version.kamon
  val kamonStatsd             = "io.kamon"                    %% "kamon-statsd"                      % Version.kamon
  val kamonAkkaHttp           = "io.kamon"                    %% "kamon-akka-http"                   % Version.kamonAkkaHttp
}

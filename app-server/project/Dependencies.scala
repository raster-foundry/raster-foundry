import scala.util.Properties

import sbt._

object Dependencies {
  val akka        = "com.typesafe.akka"  %% "akka-actor" % Version.akka
  val akkastream  = "com.typesafe.akka"  %% "akka-stream" % Version.akka
  val akkahttp    = "com.typesafe.akka"  %% "akka-http-experimental" % Version.akka
  val akkajson    = "com.typesafe.akka"  %% "akka-http-spray-json-experimental" % Version.akka
  val akkatestkit = "com.typesafe.akka"  %% "akka-http-testkit" % Version.akka
  val hikariCP    = "com.zaxxer"          % "HikariCP" % Version.hikariCP
  val slick       = "com.typesafe.slick" %% "slick" % Version.slick
  val postgres    = "org.postgresql"      % "postgresql" % Version.postgres
  val scalatest   = "org.scalatest"      %% "scalatest" % Version.scalaTest % "test"
  val slf4j       = "org.slf4j"          % "slf4j-simple" % Version.slf4j
}

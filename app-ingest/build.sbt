name := "rf-ingest"

scalaVersion := "2.11.8"

organization := "Azavea"

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

parallelExecution in Test := false

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-Yinline-warnings",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:existentials",
  "-feature"
)

shellPrompt := { s => Project.extract(s).currentProject.id + " > " }

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark-etl"              % "0.10.2",
  "com.azavea.geotrellis" %% "geotrellis-s3"                     % "0.10.2",
  "com.azavea.geotrellis" %% "geotrellis-raster"                 % "0.10.2",
  "com.azavea.geotrellis" %% "geotrellis-vector"                 % "0.10.2",
  "com.amazonaws"          % "aws-java-sdk-s3"                   % "1.9.34",
  "com.typesafe.akka"     %% "akka-http-spray-json-experimental" % "2.4.3",
  "org.apache.spark"      %% "spark-core"                        % "2.0.0" % "provided",
  "org.scalatest"         %%  "scalatest"                        % "2.2.0" % "test"
)

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}


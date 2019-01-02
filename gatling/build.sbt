enablePlugins(GatlingPlugin)

scalaVersion := "2.12.8"

scalacOptions := Seq("-encoding",
                     "UTF-8",
                     "-target:jvm-1.8",
                     "-deprecation",
                     "-feature",
                     "-unchecked",
                     "-language:implicitConversions",
                     "-language:postfixOps")

libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.0.2" % "test,it"
libraryDependencies += "io.gatling" % "gatling-test-framework" % "3.0.2" % "test,it"
libraryDependencies += "org.locationtech.geotrellis" %% "geotrellis-spark" % "3.0.0-SNAPSHOT" % "test,it"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.0" % "provided"
libraryDependencies += "io.circe" %% "circe-core" % "0.10.0"
libraryDependencies += "io.circe" %% "circe-generic" % "0.10.0"
libraryDependencies += "io.circe" %% "circe-generic-extras" % "0.10.0"
libraryDependencies += "io.circe" %% "circe-optics" % "0.10.0"
libraryDependencies += "io.circe" %% "circe-parser" % "0.10.0"
externalResolvers += "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases"
externalResolvers += "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots"

scalafmtOnCompile := true

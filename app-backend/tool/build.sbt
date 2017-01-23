name := "raster-foundry-tools"

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

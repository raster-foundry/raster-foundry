name := "rf-export"

assemblyJarName in assembly := "rf-export.jar"

mainClass in (Compile, assembly) := Some("com.azavea.rf.export.Export")

javaOptions += "-Xmx2G"

fork in run := true

// Optional deps for runing S3 Upload tests
libraryDependencies ++= Seq(
  "org.apache.hadoop" % "hadoop-aws" % "2.7.3",
  "com.amazonaws" % "aws-java-sdk" % "1.7.4"
)

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

name := "rf-batch"

assemblyJarName in assembly := "rf-batch.jar"

libraryDependencies ++= Seq(
  "org.apache.hadoop" % "hadoop-aws" % "2.7.3",
  "com.amazonaws" % "aws-java-sdk" % "1.7.4"
)

javaOptions += "-Xmx2G"

fork in run := true

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

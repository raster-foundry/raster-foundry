name := "rf-ingest"

assemblyJarName in assembly := "rf-ingest.jar"

mainClass in (Compile, assembly) := Some("com.azavea.rf.ingest.Ingest")

javaOptions += "-Xmx2G"

fork in run := true

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

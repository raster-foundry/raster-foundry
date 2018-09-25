name := "rf-batch"

assemblyJarName in assembly := "rf-batch.jar"

mainClass in (Compile, assembly) := Some("com.azavea.rf.batch.Main")

javaOptions += "-Xmx2G"

fork in run := true

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case "reference.conf"   => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
    MergeStrategy.discard
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case _                      => MergeStrategy.first
}

package com.azavea.rf.batch.ingest

import scala.util._
import java.net.URI

object CommandLine {
  case class Params(
    sceneId: String = "",
    jobDefinition: URI = new URI(""),
    testRun: Boolean = false,
    overwrite: Boolean = false,
    windowSize: Int = 1024,
    partitionsPerFile: Int = 8,
    partitionsSize: Int = 50, // partition size in mb to calculate repartitioning
    statusBucket: String = "rasterfoundry-dataproc-ingest-status-us-east-1"
  )

  // Used for reading text in as URI
  implicit val uriRead: scopt.Read[URI] =
    scopt.Read.reads(new URI(_))

  val parser = new scopt.OptionParser[Params]("raster-foundry-ingest") {
    // for debugging; prevents exit from sbt console
    override def terminate(exitState: Either[String, Unit]): Unit = ()

    head("raster-foundry-ingest", "0.1")

    opt[Unit]('t', "test").action( (_, conf) =>
      conf.copy(testRun = true) ).text("Run this job as a test - verify output")

    opt[Unit]("overwrite").action( (_, conf) =>
      conf.copy(overwrite = true) ).text("Overwrite conflicting layers")

    opt[String]('s', "sceneId")
      .action( (scene, conf) => conf.copy(sceneId = scene) )
      .text("The id of the scene to ingest")
      .required

    opt[URI]('j',"jobDefinition")
      .action( (jd, conf) => conf.copy(jobDefinition = jd) )
      .text("The location of the json which defines an ingest job")
      .required

    opt[Int]('w',"windowSize")
      .action( (s, conf) => conf.copy(windowSize = s) )
      .text("Pixel window size for streaming GeoTiff reads")

    opt[Int]('p',"partitionsPerFile")
      .action( (s, conf) => conf.copy(partitionsPerFile = s) )
      .text("Min number of RDD partitions to create per each source file")

    opt[Int]('z',"partitionsSize")
      .action( (s, conf) => conf.copy(partitionsSize = s) )
      .text("Partition size to calculate repartitioning (object size / partition size)")

    opt[String]('b',"statusBucket")
      .action( (s, conf) => conf.copy(statusBucket = s) )
      .text("S3 bucket to write status jsons to")
  }
}

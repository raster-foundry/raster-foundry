package com.azavea.rf.batch.export

import java.net.URI

import scala.util._

object CommandLine {
  case class Params(
    jobDefinition: URI = new URI(""),
    statusBucket: String = "rasterfoundry-dataproc-export-status-us-east-1"
  )

  // Used for reading text in as URI
  implicit val uriRead: scopt.Read[URI] =
    scopt.Read.reads(new URI(_))

  val parser = new scopt.OptionParser[Params]("raster-foundry-export") {
    // for debugging; prevents exit from sbt console
    override def terminate(exitState: Either[String, Unit]): Unit = ()

    head("raster-foundry-export", "0.1")

    opt[URI]('j',"jobDefinition")
      .action((jd, conf) => conf.copy(jobDefinition = jd))
      .text("The location of the json which defines an ingest job")
      .required

    opt[String]('b',"statusBucket")
      .action( (s, conf) => conf.copy(statusBucket = s) )
      .text("S3 bucket to write status jsons to")
  }
}

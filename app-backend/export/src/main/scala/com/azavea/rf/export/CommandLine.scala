package com.azavea.rf.export

import java.net.URI
import scala.util._

object CommandLine {
  case class Params(
    jobDefinition: URI = new URI(""),
    testRun: Boolean = false,
    overwrite: Boolean = false
  )

  // Used for reading text in as URI
  implicit val uriRead: scopt.Read[URI] =
    scopt.Read.reads(new URI(_))

  val parser = new scopt.OptionParser[Params]("raster-foundry-export") {
    // for debugging; prevents exit from sbt console
    override def terminate(exitState: Either[String, Unit]): Unit = ()

    head("raster-foundry-export", "0.1")

    opt[Unit]('t', "test").action( (_, conf) =>
      conf.copy(testRun = true) ).text("Run this job as a test - verify output")

    opt[Unit]("overwrite").action( (_, conf) =>
      conf.copy(overwrite = true) ).text("Overwrite conflicting layers")

    opt[URI]('j',"jobDefinition")
      .action( (jd, conf) => conf.copy(jobDefinition = jd) )
      .text("The location of the json which defines an ingest job")
      .required
  }
}

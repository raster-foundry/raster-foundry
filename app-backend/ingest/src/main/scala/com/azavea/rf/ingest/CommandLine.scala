package com.azavea.rf.ingest

import scala.util._
import java.util.HashMap
import java.net.URI
import scala.collection.JavaConversions._

object CommandLine {

  // Used for reading text in as URI
  implicit val uriRead: scopt.Read[URI] =
    scopt.Read.reads(new URI(_))

  val parser = new scopt.OptionParser[Ingest.Params]("raster-foundry-ingest") {
    // for debugging; prevents exit from sbt console
    override def terminate(exitState: Either[String, Unit]): Unit = ()

    head("raster-foundry-ingest", "0.1")

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

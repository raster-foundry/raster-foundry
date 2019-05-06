package com.rasterfoundry.lambda.overviews

import com.typesafe.scalalogging.LazyLogging
import org.backuity.clist._
import io.circe.parser._

import com.rasterfoundry.datamodel.OverviewInput

class CommandLine
    extends Command(description =
      "generates overview tif for a Raster Foundry project layer") {
  var overviewInput: String =
    arg[String](description = "json string of overview input to run")
}

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    Cli.parse(args).withCommand(new CommandLine) { command =>
      decode[OverviewInput](command.overviewInput) match {
        case Right(overviewInput) =>
          OverviewGenerator.createOverview(overviewInput) match {
            case Some(projectLayer) =>
              logger.info(
                s"Created overview and updated project layer: ${projectLayer.id}")
            case _ =>
              logger.warn(
                s"Did not update project layer, scenes were stale prior to writing layer")
          }
        case Left(e) => throw e
      }
    }
    ()
  }
}

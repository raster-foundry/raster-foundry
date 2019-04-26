package com.rasterfoundry.lambda.overviews

import org.backuity.clist._
import io.circe.parser._

class CommandLine
    extends Command(description =
      "generates overview tif for a Raster Foundry project layer") {
  var overviewInput: String =
    arg[String](description = "json string of overview input to run")
}

object Main {

  def main(args: Array[String]): Unit = {
    Cli.parse(args).withCommand(new CommandLine) { command =>
      decode[OverviewInput](command.overviewInput) match {
        case Right(overviewInput) =>
          OverviewGenerator.createOverview(overviewInput)
        case Left(e) => throw e
      }
    }
    ()
  }
}

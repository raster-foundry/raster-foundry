package com.rasterfoundry.lambda.overviews

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode

class LambdaHandler extends RequestStreamHandler with LazyLogging {

  def handleRequest(input: InputStream,
                    out: OutputStream,
                    context: Context): Unit = {
    val inputString = scala.io.Source.fromInputStream(input).mkString("")
    logger.info(s"Received input: $inputString")
    decode[OverviewInput](inputString) match {
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
}

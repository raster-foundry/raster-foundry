package com.rasterfoundry.lambda.overviews

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.parser.decode

class LambdaHandler extends RequestStreamHandler {

  def handleRequest(input: InputStream,
                    out: OutputStream,
                    context: Context): Unit = {
    decode[OverviewInput](scala.io.Source.fromInputStream(input).mkString("")) match {
      case Right(overviewInput) =>
        OverviewGenerator.createOverview(overviewInput)
      case Left(e) => throw e
    }
  }
}

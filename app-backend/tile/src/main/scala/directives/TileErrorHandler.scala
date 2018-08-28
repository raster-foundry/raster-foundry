package com.azavea.rf.tile

import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.common.RfStackTrace

import geotrellis.spark.io.LayerIOError
import akka.http.scaladsl.server.{ExceptionHandler, Directives}
import akka.http.scaladsl.model.StatusCodes
import spray.json.{SerializationException, DeserializationException}
import com.typesafe.scalalogging.LazyLogging

trait TileErrorHandler
    extends Directives
    with RollbarNotifier
    with LazyLogging {
  val tileExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
    case e: IllegalStateException =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: DeserializationException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Decoding Error", e.getMessage))
    case e: SerializationException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ServerError(500)("Encoding Error", e.getMessage))
    case e: LayerIOError =>
      logger.error(RfStackTrace(e))
      complete(
        StatusCodes.ClientError(404)("Scene or Scene tile data not found",
                                     e.getMessage))
    case e: Exception =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ServerError(500)("An unknown error occurred", ""))
  }
}

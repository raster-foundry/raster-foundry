package com.azavea.rf.common

import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import cats.data.NonEmptyList
import com.azavea.rf.tool.eval.InterpreterError
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._

// --- //

case class InterpreterException(errors: NonEmptyList[InterpreterError]) extends Exception

trait InterpreterErrorHandler extends Directives with LazyLogging {
  implicit val encodeErrorList: Encoder[NonEmptyList[InterpreterError]] =
    new Encoder[NonEmptyList[InterpreterError]] {
      final def apply(errors: NonEmptyList[InterpreterError]): Json = JsonObject.fromMap {
        Map("Errors" -> errors.toList.asJson)
      }.asJson
    }

  val interpreterExceptionHandler = ExceptionHandler {
    case ie: InterpreterException =>
      logger.debug(ie.errors.asJson.noSpaces)
      complete(ie)
  }
}

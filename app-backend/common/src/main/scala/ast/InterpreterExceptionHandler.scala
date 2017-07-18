package com.azavea.rf.common.ast

import com.azavea.rf.tool.eval.InterpreterError

import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import akka.http.scaladsl.model._
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._


case class InterpreterException(errors: NonEmptyList[InterpreterError]) extends Exception

trait InterpreterExceptionHandling extends Directives with LazyLogging {
  implicit val encodeErrorList: Encoder[NonEmptyList[InterpreterError]] =
    new Encoder[NonEmptyList[InterpreterError]] {
      final def apply(errors: NonEmptyList[InterpreterError]): Json = {
        val errMap: JsonObject = JsonObject.fromMap({
          errors.toList
            .groupBy({ _.scope })
            .map({ case (scope, error) => scope -> error.asJson })
        })
        JsonObject.fromMap(Map("Errors" -> errMap.asJson)).asJson
      }
    }

  val interpreterExceptionHandler = ExceptionHandler {
    case ie: InterpreterException =>
      logger.debug(ie.errors.asJson.noSpaces)
      complete{ (StatusCodes.BadRequest, ie.errors) }
  }
}

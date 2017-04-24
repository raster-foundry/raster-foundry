package com.azavea.rf.tile.directives

import com.azavea.rf.common.Authentication
import com.azavea.rf.tile.image._
import com.azavea.rf.tile.tool.TileSources
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import cats.data._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._


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

